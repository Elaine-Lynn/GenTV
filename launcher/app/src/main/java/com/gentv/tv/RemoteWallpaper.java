package com.gentv.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owner-requested remote still images. No service, wake lock, identifiers or background polling. */
final class RemoteWallpaper {
    static final long INTERVAL_MS = 120_000L;
    private static final int MAX_IMAGE = 8*1024*1024;
    private final ImageView image, previousImage;
    private final android.content.res.Resources resources;
    private final java.util.function.BooleanSupplier localWallpaperPresent;
    private final boolean reducedMotion;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final WallpaperCache cache;
    private List<String> playlist=new ArrayList<>();
    private long refreshedAt;
    private WallpaperPolicy policy;
    private String receivedEtag="";
    private int imageBytes;
    private boolean restored;
    private final String endpoint;
    private final boolean localTest;
    private final long interval;
    private final int width, height, dim;
    private final Bitmap.Config bitmapConfig;
    private final Runnable tick = () -> load(true);
    private volatile HttpURLConnection connection;
    private volatile boolean active, closed;
    private boolean loading;
    private boolean showingFallback;
    private int generation;
    private String displayed = "";
    private long shownAt;

    RemoteWallpaper(Context ctx,FrameLayout root,Config cfg,String endpoint,long interval,boolean localTest,java.util.function.BooleanSupplier localWallpaperPresent) {
        this.endpoint=endpoint;this.interval=interval;this.localTest=localTest;
        resources=ctx.getResources();this.localWallpaperPresent=localWallpaperPresent;
        android.app.ActivityManager memory=ctx.getSystemService(android.app.ActivityManager.class);
        boolean lowRam=memory!=null && memory.isLowRamDevice();
        bitmapConfig=lowRam?Bitmap.Config.RGB_565:Bitmap.Config.ARGB_8888;
        width=Math.min(lowRam?1280:1920,ctx.getResources().getDisplayMetrics().widthPixels);
        height=Math.min(lowRam?720:1080,ctx.getResources().getDisplayMetrics().heightPixels);
        dim=cfg.wallpaperDim;
        cache=new WallpaperCache(new File(ctx.getFilesDir(),localTest?"wallpapers-test":"wallpapers"),endpoint);
        reducedMotion=cfg.reducedMotion;
        previousImage=new ImageView(ctx);previousImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previousImage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image=new ImageView(ctx);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Above the generated sky/glow; below captions, clock, controls and app tiles.
        root.addView(previousImage,root.indexOfChild(root.findViewById(R.id.ambient))+1,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(image,root.indexOfChild(previousImage)+1,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        showFallback();
    }
    /** Draw the bundled abstract wallpaper before the first frame; never wait for a network response. */
    private void showFallback() {
        showingFallback=true;
        finishTransition();
        if(localWallpaperPresent.getAsBoolean()) { image.setImageDrawable(null); return; }
        try {
            Bitmap bitmap=decode(TvCloud.read(resources.openRawResource(R.raw.wallpaper_default),MAX_IMAGE));
            if(bitmap!=null) image.setImageBitmap(bitmap);
        } catch(java.io.IOException failure) {
            android.util.Log.e("GenTV.Wallpaper","Bundled wallpaper unavailable",failure);
        }
    }
    void localWallpaperReady() {
        if(showingFallback && localWallpaperPresent.getAsBoolean()) {
            finishTransition();image.setImageDrawable(null);
        }
    }
    void start() {
        if(closed || active)return;
        if(image.getDrawable()==null) showFallback();
        if(endpoint.isEmpty())return;
        active=true;generation++;
        load(shownAt!=0 && SystemClock.elapsedRealtime()-shownAt>=interval);
    }
    void stop() {
        active=false;generation++;main.removeCallbacks(tick);
        finishTransition();
        HttpURLConnection c=connection;if(c!=null)c.disconnect();
    }
    void close() { stop();closed=true;worker.shutdownNow(); }
    private void finishTransition() {
        image.animate().cancel(); image.setAlpha(1f); previousImage.setImageDrawable(null);
    }
    private void show(Bitmap bitmap) {
        showingFallback=false;
        finishTransition();
        if (!reducedMotion) previousImage.setImageDrawable(image.getDrawable());
        image.setImageBitmap(bitmap);
        if (reducedMotion) return;
        image.setAlpha(0f);
        image.animate().alpha(1f).setDuration(1600)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(()->previousImage.setImageDrawable(null)).start();
    }
    void releaseForMemoryPressure() { finishTransition();image.setImageDrawable(null);displayed="";shownAt=0; }
    private void schedule() {
        main.removeCallbacks(tick);
        if(active && !closed)main.postDelayed(tick,Math.max(1,shownAt==0?interval:interval-(SystemClock.elapsedRealtime()-shownAt)));
    }
    private void load(boolean advance) {
        if(!active || closed || loading)return;
        loading=true;int request=generation;String previousDisplay=displayed;
        worker.execute(()->{
            String previous=previousDisplay;
            Bitmap bitmap=null;String next=previous;boolean empty=false;boolean networkAllowed=true;
            try {
                if(policy==null)policy=new WallpaperPolicy(cache.policy());
                boolean cold=!restored;
                if(cold) {
                    restored=true;
                    try {
                        JSONObject state=cache.state();
                        playlist=parse(state.toString(),localTest);
                        refreshedAt=state.optLong("fetchedAt",0);
                        if(previous.isEmpty()) previous=state.optString("selected","");
                    }catch(Exception ignored) { }
                }
                long now=System.currentTimeMillis();
                long refreshInterval=localTest?1000:WallpaperPolicy.REFRESH;
                policy.seedManifest(refreshedAt,refreshInterval);
                boolean refresh=policy.manifestDue(now,refreshInterval) && (!cold || playlist.isEmpty());
                if(refresh)try {
                    // Persist a failed-attempt cooldown before opening the connection, including across restarts.
                    policy.manifestFailure(now);cache.savePolicy(policy);
                    byte[] body=fetch(endpoint,256*1024,cache.etag);
                    if(body!=null) {
                        List<String> updated=parse(new String(body,java.nio.charset.StandardCharsets.UTF_8),localTest);
                        playlist=updated;cache.etag=receivedEtag;
                    }
                    refreshedAt=now;policy.manifestSuccess(now,refreshInterval);
                    cache.saveState(playlist,refreshedAt,previous);cache.prune(playlist);
                }catch(Exception failure) {
                    networkAllowed=false;
                }
                List<String> local=cache.available(playlist);
                for(String url:local)policy.downloaded(url,now);
                // One new image per tick; ordinary rotation reads only complete local images.
                if(networkAllowed && active && !closed)for(String candidate:playlist) {
                    if(local.contains(candidate) || !policy.mayDownload(candidate,now))continue;
                    if(cache.needsReplacement(MAX_IMAGE) && !policy.mayReplace(now))break;
                    if(!policy.reserve(now))break;
                    imageBytes=0;
                    try {
                        cache.savePolicy(policy); // Reserve worst-case traffic before GET.
                        byte[] data=fetch(candidate,MAX_IMAGE,"");
                        Bitmap validated=decode(data);
                        if(validated==null)throw new IllegalArgumentException("Unreadable wallpaper");
                        validated.recycle();
                        boolean replacement=cache.needsReplacement(data.length);
                        // Spend the replacement allowance before evicting, including across process death.
                        boolean allowed=policy.mayReplace(now);
                        if(replacement && allowed){policy.replaced(now);cache.savePolicy(policy);}
                        if(cache.store(candidate,data,previousDisplay,allowed))policy.downloaded(candidate,now);
                        else policy.failed(candidate,now);
                    }catch(Exception | OutOfMemoryError failure) {policy.failed(candidate,now);}
                    finally {policy.finish(imageBytes);cache.savePolicy(policy);}
                    break;
                }
                local=cache.available(playlist);
                List<String> known=new ArrayList<>(playlist);known.addAll(local);policy.retain(known);
                cache.savePolicy(policy);
                empty=playlist.isEmpty() && local.isEmpty();
                if(!empty) {
                    int index=local.indexOf(previous);
                    int start=index<0?0:advance?(index+1)%Math.max(1,local.size()):index;
                    Exception last=null;boolean ready=false;
                    for(int offset=0;offset<local.size();offset++) {
                        String candidate=local.get((start+offset)%local.size());
                        try {
                            if(!candidate.equals(previousDisplay) || shownAt==0) {
                                byte[] data=cache.read(candidate);
                                Bitmap decoded=data==null?null:decode(data);
                                if(decoded==null) {
                                    policy.failed(candidate,now);
                                    cache.remove(candidate);cache.savePolicy(policy);
                                    throw new IllegalArgumentException("Unreadable cached image");
                                }
                                bitmap=decoded;
                            }
                            next=candidate;ready=true;break;
                        }catch(Exception badImage) {last=badImage;}
                    }
                    if(!ready && last!=null)throw last;
                    cache.saveState(playlist,refreshedAt,next);
                }
            }catch(Exception | OutOfMemoryError failure) {
                next=previous;
                android.util.Log.w("GenTV.Wallpaper","Wallpaper unavailable; preserving background: "+failure.getClass().getSimpleName());
            }
            Bitmap result=bitmap;String selected=next;boolean clear=empty;
            main.post(()->{
                loading=false;
                if(!active || closed || request!=generation) {
                    if(result!=null)result.recycle();
                    if(active && !closed)load(false);
                    return;
                }
                if(clear) {showFallback();displayed="";shownAt=0;}
                else if(result!=null) {show(result);displayed=selected;}
                shownAt=SystemClock.elapsedRealtime();schedule();
            });
        });
    }
    static List<String> parse(String json,boolean localTest) throws Exception {
        JSONObject body=new JSONObject(json);JSONArray entries=body.getJSONArray("wallpapers");
        if(entries.length()>100)throw new IllegalArgumentException("Too many wallpapers");
        List<String> result=new ArrayList<>();
        for(int i=0;i<entries.length();i++) {
            String url=entries.getJSONObject(i).getString("url");checked(url,localTest);
            if(!result.contains(url))result.add(url);
        }
        return result;
    }
    private static URI checked(String value,boolean localTest) throws Exception {
        URI uri=new URI(value);
        if(value.length()>2048 || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null
                || !("https".equals(uri.getScheme()) || localTest && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())))
            throw new IllegalArgumentException("HTTPS wallpaper required");
        return uri;
    }
    private byte[] fetch(String value,int limit,String etag) throws Exception {
        URI uri=checked(value,localTest);
        for(int hop=0;hop<5;hop++) {
            if(!active || closed)throw new java.io.InterruptedIOException("Home is hidden");
            HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();connection=c;
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent","GenTV-Wallpaper/1");
            c.setRequestProperty("Accept-Encoding","identity");
            if(!etag.isEmpty())c.setRequestProperty("If-None-Match",etag);
            c.setRequestProperty("Accept",limit==MAX_IMAGE?"image/jpeg,image/png,image/webp":"application/json");
            try {
                int code=c.getResponseCode();
                if(code==304 && limit!=MAX_IMAGE && !etag.isEmpty())return null;
                if(code>=300 && code<400) {uri=checked(uri.resolve(c.getHeaderField("Location")).toString(),localTest);continue;}
                if(code!=200 || c.getContentLengthLong()>limit)throw new IllegalArgumentException("Wallpaper response refused");
                receivedEtag=c.getHeaderField("ETag");if(receivedEtag==null)receivedEtag="";
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];int n;
                    while(out.size()<limit && (n=in.read(buffer,0,Math.min(buffer.length,limit-out.size())))!=-1) {
                        if(limit==MAX_IMAGE)imageBytes+=n;
                        if(!active || closed)throw new java.io.InterruptedIOException("Home is hidden");
                        out.write(buffer,0,n);
                    }
                    if(out.size()==limit && c.getContentLengthLong()!=limit)throw new IllegalArgumentException("Wallpaper too large");
                    if(c.getContentLengthLong()>=0 && out.size()!=c.getContentLengthLong())throw new java.io.IOException("Incomplete wallpaper");
                    return out.toByteArray();
                }
            }finally {c.disconnect();connection=null;}
        }
        throw new IllegalArgumentException("Too many redirects");
    }
    private Bitmap decode(byte[] bytes) {
        if(bytes.length>MAX_IMAGE)return null;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<1 || o.outHeight<1 || o.outWidth>16384 || o.outHeight>16384)return null;
        o.inSampleSize=1;
        while(o.outWidth/o.inSampleSize>width*2 || o.outHeight/o.inSampleSize>height*2)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;o.inPreferredConfig=bitmapConfig;o.inMutable=dim>0;
        Bitmap original=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(original==null)return null;
        float scale=Math.min(1f,Math.min((float)width/original.getWidth(),(float)height/original.getHeight()));
        Bitmap resized=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),Math.max(1,Math.round(original.getHeight()*scale)),true);
        if(resized!=original)original.recycle();
        if(dim>0)new Canvas(resized).drawColor(Color.argb(dim*255/100,0,0,0));
        // Full colour avoids banding in skies; a hardware bitmap avoids retaining a second CPU copy.
        if(bitmapConfig==Bitmap.Config.ARGB_8888) {
            Bitmap hardware=resized.copy(Bitmap.Config.HARDWARE,false);
            if(hardware!=null) {resized.recycle();return hardware;}
        }
        return resized;
    }
}
