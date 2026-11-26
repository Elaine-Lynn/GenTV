package com.gentv.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** GHCP 1 native host. Explicit async IPC; no downloaded code, network or ADB permissions. */
final class HomeContentController {
    interface Host {
        boolean allowedNow();
        void returnToTile(String key);
        void topBar();
        void contentVisible(boolean visible);
        void openContent(Intent intent);
    }
    static final int REQUEST=1, SNAPSHOT=2, INVALIDATE=3;
    private static final int IMAGE_BYTES=2*1024*1024;
    private final Activity activity;
    private final Host host;
    private final FrameLayout root, content, backgrounds;
    private final Handler main=new Handler(Looper.getMainLooper());
    // Even a broken remote provider cannot create an unbounded collection of blocked reader threads.
    private static final ThreadPoolExecutor workers=pool(2,8,"control");
    private static final ThreadPoolExecutor imageWorkers=pool(2,8,"images");
    private static final ThreadPoolExecutor cancellations=pool(1,16,"cancel");
    private static ThreadPoolExecutor pool(int size,int queue,String name){
        ThreadPoolExecutor result=new ThreadPoolExecutor(size,size,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(queue),
                r->{Thread t=new Thread(r,"GenTV-content-"+name);t.setDaemon(true);return t;});
        result.allowCoreThreadTimeOut(true);return result;
    }
    private final List<Future<?>> jobs=new ArrayList<>();
    private final List<android.os.CancellationSignal> signals=new ArrayList<>();
    private final List<Bitmap> images=new ArrayList<>();
    private final float density;
    private final boolean animations,reduced;
    private HomeContentRegistry.Extension extension;
    private HomeContentModel snapshot;
    private ServiceConnection connection;
    private Messenger reply;
    private String key;
    private String backgroundKey;
    private int generation, direction;
    private long expiresAt, imageBytes, lastInvalidation;
    private View pane, outgoing;
    private View firstAction;
    private boolean disposed, received, acceptingImages, retried;
    private int availableHeight;
    private HomeChannelPreview channelPreview;
    private final Runnable requestTimeout=this::requestTimedOut;
    private void requestTimedOut(){
        // One cold-start retry per focus, never an unbounded polling loop. Home remains usable throughout.
        if(!retried&&key!=null&&extension!=null&&snapshot==null&&host.allowedNow()){
            retried=true;disconnect();int token=++generation;
            main.postDelayed(()->request(token),500);
        }else stop(false);
    }
    private final Runnable expiry=()->stop(true);
    private final Runnable refresh=this::refreshSnapshot;
    private void refreshSnapshot(){if(key!=null&&host.allowedNow())request(++generation);}

    HomeContentController(Activity activity,FrameLayout root,boolean animations,boolean reduced,Host host) {
        this.activity=activity;this.root=root;this.animations=animations;this.reduced=reduced;this.host=host;
        density=activity.getResources().getDisplayMetrics().density;
        backgrounds=new FrameLayout(activity);backgrounds.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Remote/local wallpaper layers already exist. Our temporary artwork sits above them, below UI.
        root.addView(backgrounds,root.indexOfChild(root.findViewById(R.id.heroScrim)),new FrameLayout.LayoutParams(-1,-1));
        content=new FrameLayout(activity);content.setClipChildren(false);content.setClipToPadding(false);
        root.addView(content,new FrameLayout.LayoutParams(-1,1));
    }
    private int dp(float n){return Math.round(n*density);}
    private long duration(long n){
        if(!animations||!CompatApi.animatorsEnabled())return 0;
        return reduced?Math.min(n,120):n;
    }
    boolean owns(View view){for(View v=view;v!=null;){if(v==content)return true;android.view.ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}return false;}
    boolean inContent(){return owns(activity.getCurrentFocus());}
    boolean visible(){return pane!=null&&pane.getAlpha()>0;}
    boolean hasContentPixels(){return pane!=null||outgoing!=null||backgrounds.getChildCount()>0;}
    boolean canMoveDown(){return channelPreview!=null&&channelPreview.canMoveDown();}
    boolean enter(){if(snapshot==null||SystemClock.elapsedRealtime()>=expiresAt||!host.allowedNow()||firstAction==null)return false;return firstAction.requestFocus();}
    boolean back(){if(!inContent())return false;host.returnToTile(key);return true;}
    String sourceKey(){return key;}

    void focus(String next,int visualDirection,int top,int bottom) {
        int margin=dp(28);
        int height=bottom-top-margin;
        if(!host.allowedNow()||next==null||height<dp(110)){stop(true);return;}
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)content.getLayoutParams();
        lp.width=Math.max(1,root.getWidth()-dp(112));lp.height=height;lp.leftMargin=dp(56);lp.topMargin=top;
        content.setLayoutParams(lp);availableHeight=height;
        if(next.equals(key)&&snapshot!=null&&SystemClock.elapsedRealtime()<expiresAt)return;
        if(next.equals(key)&&connection!=null)return;
        direction=visualDirection;stop(false);key=next;
        int token=generation;
        main.postDelayed(()->{
            if(!current(token))return;
            submit(()->{HomeContentRegistry.Extension e=HomeContentRegistry.find(activity,next);
                main.post(()->{if(!current(token))return;if(e==null||!HomeContentRegistry.allowed(activity,e)){stop(false);return;}
                    extension=e;request(token);});});
        },200);
    }
    private boolean current(int token){return !disposed&&token==generation&&key!=null&&host.allowedNow();}
    private void request(int token) {
        if(!current(token)||extension==null)return;
        disconnect();received=false;
        final HomeContentRegistry.Extension expected=extension;
        reply=new Messenger(new Handler(Looper.getMainLooper(),message->{
            if(!current(token)||extension!=expected||CompatApi.sendingUid(message)!=expected.uid||message.arg1!=token)return true;
            if(message.what==INVALIDATE){
                String source=key;long now=SystemClock.elapsedRealtime();boolean refetch=now-lastInvalidation>=1000;
                lastInvalidation=now;stop(true);
                if(refetch&&source!=null&&host.allowedNow())host.returnToTile(source);
                return true;
            }
            if(message.what!=SNAPSHOT||received)return true;
            received=true;
            try {
                String json=message.getData().getString("snapshot");
                if(json==null||json.length()>HomeContentModel.MAX_BYTES){stop(false);return true;}
                submit(()->{
                    try {
                        HomeContentModel model=HomeContentModel.parse(json);
                        validate(model,expected);
                        main.post(()->{if(current(token)&&extension==expected){main.removeCallbacks(requestTimeout);show(model,token);}});
                    } catch(Exception|LinkageError invalid){main.post(()->{if(current(token))stop(false);});}
                });
            }catch(RuntimeException invalid){stop(false);}
            return true;
        }));
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(!current(token)||extension!=expected)return;
                Message request=Message.obtain(null,REQUEST);request.arg1=token;request.replyTo=reply;
                Bundle b=new Bundle();b.putInt("protocolMajor",1);b.putString("component",expected.activity.flattenToShortString());
                b.putString("locale",CompatApi.languageTag(CompatApi.locale(activity.getResources().getConfiguration())));
                b.putInt("widthDp",(int)(content.getWidth()/density));b.putInt("heightDp",(int)(availableHeight/density));
                b.putBoolean("channelPreview",true);
                request.setData(b);
                submit(()->{try{new Messenger(binder).send(request);}catch(Exception e){main.post(()->{if(current(token))stop(false);});}});
            }
            @Override public void onServiceDisconnected(ComponentName name){if(current(token))stop(true);}
            @Override public void onBindingDied(ComponentName name){if(current(token))stop(true);}
            @Override public void onNullBinding(ComponentName name){if(current(token))stop(false);}
        };
        main.removeCallbacks(requestTimeout);main.postDelayed(requestTimeout,1000);
        try{if(!activity.bindService(new Intent(HomeContentRegistry.ACTION).setComponent(expected.service),connection,Context.BIND_AUTO_CREATE))stop(false);}
        catch(RuntimeException failure){stop(false);}
    }
    private void validate(HomeContentModel model,HomeContentRegistry.Extension e) throws Exception {
        HomeContentRegistry.Extension now=HomeContentRegistry.find(activity,e.key());
        if(now==null||now.uid!=e.uid||!now.identity().equals(e.identity())||!HomeContentRegistry.allowed(activity,now))
            throw new IllegalArgumentException("changed provider identity");
        for(HomeContentModel.Item item:model.items){
            if(!item.activity.isEmpty())action(item,e);
            if (!item.video.isEmpty()) {
                Uri uri=Uri.parse(item.video);
                if (!e.activity.getPackageName().equals(uri.getAuthority()))
                    throw new IllegalArgumentException("foreign video");
                android.content.res.Resources resources=activity.getPackageManager()
                        .getResourcesForApplication(e.activity.getPackageName());
                int id=Integer.parseInt(uri.getLastPathSegment());
                if (!"raw".equals(resources.getResourceTypeName(id)))
                    throw new IllegalArgumentException("video resource type");
                try (AssetFileDescriptor file=resources.openRawResourceFd(id)) {
                    if (file==null||file.getLength()<=0||file.getLength()>4*1024*1024)
                        throw new IllegalArgumentException("video size");
                }
            }
        }
    }
    private Uri validateImage(String value,HomeContentRegistry.Extension e) throws Exception {
        if(value.isEmpty())return null;
        Uri uri=Uri.parse(value);
        if(uri.getFragment()!=null||uri.getQuery()!=null||uri.getUserInfo()!=null)throw new IllegalArgumentException("image URI");
        if("android.resource".equals(uri.getScheme())){
            if(!e.activity.getPackageName().equals(uri.getAuthority()))throw new IllegalArgumentException("foreign resource");
        }else if("content".equals(uri.getScheme())){
            ProviderInfo provider=activity.getPackageManager().resolveContentProvider(uri.getAuthority(),0);
            if(provider==null||provider.applicationInfo.uid!=e.uid||!provider.packageName.equals(e.activity.getPackageName()))throw new IllegalArgumentException("foreign provider");
        }else throw new IllegalArgumentException("image scheme");
        return uri;
    }
    private Intent action(HomeContentModel.Item item,HomeContentRegistry.Extension e) throws Exception {
        ComponentName target=ComponentName.unflattenFromString(item.activity);
        if(target==null||!target.getPackageName().equals(e.activity.getPackageName()))throw new IllegalArgumentException("foreign action");
        ActivityInfo a=activity.getPackageManager().getActivityInfo(target,0);
        if(!a.enabled||!a.exported||!a.applicationInfo.enabled||a.applicationInfo.uid!=e.uid
                ||a.permission!=null&&!a.permission.isEmpty()&&!CompatApi.permissionGranted(activity,a.permission))
            throw new IllegalArgumentException("unavailable action");
        return new Intent(Intent.ACTION_VIEW).setComponent(target).putExtra("com.gentv.tv.home.ACTION_ID",item.actionId);
    }
    private void show(HomeContentModel model,int token) {
        boolean wasFocused=inContent();
        String selected=wasFocused&&activity.getCurrentFocus().getTag() instanceof String?(String)activity.getCurrentFocus().getTag():null;
        snapshot=model;expiresAt=SystemClock.elapsedRealtime()+model.ttlSeconds*1000L;
        main.removeCallbacks(expiry);main.postDelayed(expiry,model.ttlSeconds*1000L);
        // Content expiry is a hard boundary; refresh is foreground-only and bounded by the same session.
        main.removeCallbacks(refresh);
        if(model.ttlSeconds>2)main.postDelayed(refresh,Math.max(1000,model.ttlSeconds*1000L-1000));
        if(model.items.isEmpty()){boolean focused=inContent();clearPresentation(false);if(focused)host.returnToTile(key);return;}
        if(outgoing!=null){outgoing.animate().cancel();content.removeView(outgoing);outgoing=null;}
        View previousPane=pane;
        boolean refreshing=previousPane!=null;
        boolean channels=HomeContentModel.CHANNEL_PREVIEW.equals(model.template);
        if(channelPreview!=null){channelPreview.release();channelPreview=null;}
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);panel.setClipToPadding(false);
        if(channels){
            channelPreview=new HomeChannelPreview(activity, model, extension,
                    ()->validAction(model,extension), ()->host.returnToTile(key), host::topBar);
            panel.addView(channelPreview,new LinearLayout.LayoutParams(-1,-1));
            firstAction=channelPreview.initialFocus();
        }else{
            boolean isSummary="summary".equals(model.template);
            if(isSummary){
                panel.setContentDescription(extension.label);
            }else{
                TextView source=text(extension.label,13,0xffcad4e3,null);source.setMaxLines(1);panel.addView(source);
            }
            TextView title=text(model.title,isSummary?30:24,isSummary?0xFCFFFFFF:Color.WHITE,
                    isSummary?UiFont.get(activity,R.string.font_regular):null);title.setMaxLines(2);panel.addView(title);
            if(!model.subtitle.isEmpty()&&availableHeight>=dp(210)){
                TextView sub=text(model.subtitle,isSummary?13:14,isSummary?0xC2DCE8F6:0xffd4deea,
                        isSummary?UiFont.get(activity,R.string.font_regular):null);sub.setMaxLines(2);
                if(isSummary){LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2);slp.topMargin=dp(3);panel.addView(sub,slp);}
                else panel.addView(sub);
            }
            LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);row.setClipChildren(false);row.setClipToPadding(false);if(isSummary)row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(isSummary?16:12),0,dp(4));
            HorizontalScrollView shelf=new HorizontalScrollView(activity);shelf.setClipChildren(false);shelf.setClipToPadding(false);
            shelf.setHorizontalScrollBarEnabled(false);shelf.setFillViewport(true);
            shelf.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));
            panel.addView(shelf,new LinearLayout.LayoutParams(-1,0,1));
            firstAction=null;List<View> actions=new ArrayList<>();
            int visibleCount=Math.min(3,model.items.size());
            int gap=dp(isSummary?18:12);
            int cardWidth=isSummary
                    ?Math.max(dp(120),(root.getWidth()-dp(112)-gap*(visibleCount-1))/visibleCount)
                    :Math.max(dp(120),(root.getWidth()-dp(124))/visibleCount);
            int index=0;
            for(HomeContentModel.Item item:model.items){
                LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setClipChildren(false);card.setClipToPadding(false);
                if(isSummary){
                    card.setGravity(Gravity.CENTER_VERTICAL);
                    card.setPadding(dp(24),dp(20),dp(24),dp(20));
                    card.setMinimumHeight(dp(116));
                    card.setBackground(summarySurface(false));
                }else{
                    card.setPadding(dp(10),dp(8),dp(10),dp(8));
                    card.setBackground(surface(false));
                }
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(cardWidth,isSummary?-2:-1);
                if(index<model.items.size()-1||!isSummary)cp.setMarginEnd(gap);
                row.addView(card,cp);
                boolean picture=!item.image.isEmpty()&&!isSummary;
                if(picture){ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    card.addView(image,new LinearLayout.LayoutParams(-1,0,1));load(item.image,image,false,token,extension);}
                TextView label=text(item.title,isSummary?36:17,isSummary?0xFCFFFFFF:Color.WHITE,
                        isSummary?UiFont.get(activity,R.string.font_light):null);
                label.setMaxLines(2);
                if(isSummary)label.setIncludeFontPadding(false);
                card.addView(label);
                if(!item.subtitle.isEmpty()){
                    TextView sub=text(item.subtitle,13,isSummary?0xC4DCE9F8:0xffd4deea,
                            isSummary?UiFont.get(activity,R.string.font_regular):null);sub.setMaxLines(2);
                    if(isSummary){LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2);slp.topMargin=dp(6);card.addView(sub,slp);}
                    else card.addView(sub);
                }
                if(item.progress>=0){ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(item.progress*1000));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));}
                if(!item.activity.isEmpty()){
                    View target;
                    if(isSummary){
                        target=card;
                        card.setOnFocusChangeListener((v,f)->{
                            v.setBackground(summarySurface(f));
                            float scale=f&&!reduced?1.025f:1f;
                            v.animate().scaleX(scale).scaleY(scale).setDuration(duration(180)).start();
                        });
                    }else{
                        TextView button=text(activity.getString(R.string.home_content_open),14,Color.WHITE,null);
                        button.setPadding(dp(12),dp(7),dp(12),dp(7));button.setGravity(Gravity.CENTER);
                        button.setBackground(surface(false));
                        button.setOnFocusChangeListener((v,f)->v.setBackground(surface(f)));
                        card.addView(button);
                        target=button;
                    }
                    target.setFocusable(true);target.setFocusableInTouchMode(true);target.setClickable(true);target.setTag(item.id);target.setId(View.generateViewId());target.setContentDescription(item.title+" · "+activity.getString(R.string.home_content_open));
                    target.setOnClickListener(v->{
                        HomeContentRegistry.Extension e=extension;
                        if(!validAction(model,e))return;
                        submit(()->{try{HomeContentRegistry.Extension now=HomeContentRegistry.find(activity,e.key());
                            if(now==null||!now.identity().equals(e.identity()))return;
                            Intent intent=action(item,now);main.post(()->{if(validAction(model,e))host.openContent(intent);});
                        }catch(Exception ignored){main.post(()->{if(current(token))stop(true);});}});
                    });
                    target.setOnKeyListener((v,code,event)->{
                        if(code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_DOWN)host.returnToTile(key);return true;}
                        if(code==KeyEvent.KEYCODE_DPAD_UP){if(event.getAction()==KeyEvent.ACTION_DOWN)host.topBar();return true;}
                        return false;
                    });
                    actions.add(target);if(firstAction==null)firstAction=target;
                }
                ++index;
            }
            boolean rtl=root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL||activity.getResources().getBoolean(R.bool.rtl_app_grid);
            row.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
            shelf.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
            for(int i=0;i<actions.size();i++){
                View b=actions.get(i);int left=rtl?Math.min(actions.size()-1,i+1):Math.max(0,i-1);int right=rtl?Math.max(0,i-1):Math.min(actions.size()-1,i+1);
                b.setNextFocusLeftId(actions.get(left).getId());b.setNextFocusRightId(actions.get(right).getId());
            }
        }
        pane=panel;content.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        panel.setAlpha(refreshing?1:0);panel.setTranslationX(refreshing||reduced?0:dp(20)*direction);panel.setTranslationY(refreshing||reduced||direction!=0?0:dp(20));
        panel.animate().alpha(1).translationX(0).translationY(0).setDuration(refreshing?0:duration(280)).setInterpolator(new DecelerateInterpolator(2)).start();
        host.contentVisible(true);
        if(wasFocused){View match=selected==null?null:panel.findViewWithTag(selected);if(match!=null)match.requestFocus();else if(firstAction!=null)firstAction.requestFocus();else host.returnToTile(key);}
        // Transfer input focus before retiring its old parent. Otherwise the global focus observer
        // sees a transient null focus and would cancel a perfectly healthy foreground refresh.
        if(previousPane!=null){
            disable(previousPane);previousPane.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            previousPane.animate().cancel();content.removeView(previousPane);
        }
        pruneImages();
        if(model.background.isEmpty())fadeBackground();
        else if(!model.background.equals(backgroundKey)){fadeBackground();load(model.background,null,true,token,extension);}
    }
    private boolean validAction(HomeContentModel displayed,HomeContentRegistry.Extension source){
        // A background refresh changes the request token, not the validity of the still-visible snapshot.
        return !disposed&&source!=null&&extension==source&&snapshot==displayed&&key!=null
                &&host.allowedNow()&&SystemClock.elapsedRealtime()<expiresAt&&HomeContentRegistry.allowed(activity,source);
    }
    private TextView text(String value,int size,int color,Typeface font){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(font!=null?font:Typeface.create("sans-serif",Typeface.NORMAL));t.setEllipsize(TextUtils.TruncateAt.END);t.setShadowLayer(dp(3),0,dp(1),0x99000000);return t;}
    private GradientDrawable surface(boolean focused){GradientDrawable d=new GradientDrawable();d.setColor(focused?0x66344359:0x44111c2c);d.setCornerRadius(dp(9));if(focused)d.setStroke(dp(2),Color.WHITE);return d;}
    private GradientDrawable summarySurface(boolean focused){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,focused?new int[]{0x70263D5E,0x441B2D48,0x1C111E32}:new int[]{0x46101E34,0x220D182B,0x06091220});d.setCornerRadius(dp(20));if(focused)d.setStroke(dp(1.5f),0xD9FFFFFF);return d;}
    private void disable(View v){v.setFocusable(false);v.setEnabled(false);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)disable(((ViewGroup)v).getChildAt(i));}
    private void load(String value,ImageView view,boolean background,int token,HomeContentRegistry.Extension e){
        acceptingImages=true;
        android.os.CancellationSignal signal=new android.os.CancellationSignal();signals.add(signal);
        long deadline=SystemClock.elapsedRealtime()+2000;
        submitTo(imageWorkers,()->{
            Bitmap bitmap=null;
            try {
                Uri uri=validateImage(value,e);
                if(uri==null)return;
                byte[] bytes;
                try(AssetFileDescriptor file=CompatApi.openAsset(activity,uri,signal)){
                    if(file==null||file.getLength()>IMAGE_BYTES)throw new IllegalArgumentException("image bytes");
                    try(InputStream input=file.createInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[8192];int read,total=0;
                        while((read=input.read(buffer))!=-1){if(signal.isCanceled()||(total+=read)>IMAGE_BYTES||SystemClock.elapsedRealtime()>deadline)throw new IllegalArgumentException("image limit");out.write(buffer,0,read);}bytes=out.toByteArray();
                    }
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>8192||bounds.outHeight>8192)throw new IllegalArgumentException("dimensions");
                BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=1;
                int maxW=background?1280:480,maxH=background?720:300;
                while((bounds.outWidth/decode.inSampleSize)>maxW||(bounds.outHeight/decode.inSampleSize)>maxH)decode.inSampleSize*=2;
                decode.inPreferredConfig=Bitmap.Config.RGB_565;
                bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,decode);
            }catch(Exception|LinkageError invalid){/* A bad optional image never invalidates text. */}
            Bitmap result=bitmap;
            main.post(()->{
                signals.remove(signal);
                if(result==null)return;
                if(!current(token)||extension!=e||!acceptingImages||SystemClock.elapsedRealtime()>deadline
                        ||SystemClock.elapsedRealtime()>=expiresAt||imageBytes+CompatApi.allocationBytes(result)>12L*1024*1024){result.recycle();return;}
                images.add(result);imageBytes+=CompatApi.allocationBytes(result);
                if(background){backgroundKey=value;showBackground(result);}else if(view.getParent()!=null)view.setImageBitmap(result);
            });
        });
        main.postDelayed(()->{signals.remove(signal);cancelSignal(signal);},2000);
    }
    private void showBackground(Bitmap bitmap){
        // Only old + new: interrupted transitions never accumulate full-screen layers.
        while(backgrounds.getChildCount()>1)removeImage((ImageView)backgrounds.getChildAt(0));
        ImageView old=backgrounds.getChildCount()==0?null:(ImageView)backgrounds.getChildAt(0);
        if(old!=null){old.animate().cancel();old.animate().alpha(0).setDuration(duration(700)).withEndAction(()->removeImage(old)).start();}
        ImageView next=new ImageView(activity);next.setScaleType(ImageView.ScaleType.CENTER_CROP);next.setImageBitmap(bitmap);next.setColorFilter(0x55000000);next.setAlpha(0f);
        backgrounds.addView(next,new FrameLayout.LayoutParams(-1,-1));next.animate().alpha(1).setDuration(duration(700)).start();
    }
    private void removeImage(ImageView image){image.animate().cancel();image.setImageDrawable(null);backgrounds.removeView(image);main.post(this::pruneImages);}
    private void fadeBackground(){backgroundKey=null;for(int i=0;i<backgrounds.getChildCount();i++){ImageView v=(ImageView)backgrounds.getChildAt(i);v.animate().cancel();v.animate().alpha(0).setDuration(duration(700)).withEndAction(()->removeImage(v)).start();}}
    private void disconnect(){if(connection!=null){try{activity.unbindService(connection);}catch(RuntimeException ignored){}connection=null;}reply=null;}
    private void cancelSignal(android.os.CancellationSignal signal){
        // Cancellation may call a remote provider; never perform it on the UI thread.
        try{cancellations.execute(()->{try{signal.cancel();}catch(RuntimeException ignored){}});}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    private void submit(Runnable task){submitTo(workers,task);}
    private void submitTo(ThreadPoolExecutor executor,Runnable task){
        jobs.removeIf(Future::isDone);
        try{jobs.add(executor.submit(task));}catch(java.util.concurrent.RejectedExecutionException busy){/* Bounded timeout recovers. */}
    }
    private void clearPresentation(boolean immediate){
        if(channelPreview!=null){channelPreview.release();channelPreview=null;}
        firstAction=null;snapshot=null;acceptingImages=false;
        // Only a pane that was shown may hand the shelf back. Every focus move stops the previous session, and a
        // spurious "hidden" here cancels the dock title's fade-out before it swaps in the newly focused app's name.
        boolean shown=pane!=null;
        if(outgoing!=null){outgoing.animate().cancel();content.removeView(outgoing);outgoing=null;}
        if(pane!=null){View old=pane;pane=null;disable(old);old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            old.animate().cancel();if(immediate)content.removeView(old);else{outgoing=old;old.animate().alpha(0).translationX(reduced?0:-dp(20)*direction).translationY(reduced||direction!=0?0:dp(16)).setDuration(duration(180)).withEndAction(()->{content.removeView(old);if(outgoing==old)outgoing=null;}).start();}}
        if(immediate){for(int i=backgrounds.getChildCount()-1;i>=0;i--)removeImage((ImageView)backgrounds.getChildAt(i));}else fadeBackground();
        if(shown)host.contentVisible(false);
        // Bitmaps may still be referenced by an outgoing frame. Drop ownership; Android frees them once views retire.
        images.clear();imageBytes=0;
    }
    void stop(boolean immediate){
        boolean focused=inContent();String previousKey=key;
        ++generation;main.removeCallbacksAndMessages(null);disconnect();
        for(Future<?> job:jobs)job.cancel(true);jobs.clear();workers.purge();imageWorkers.purge();
        for(android.os.CancellationSignal s:new ArrayList<>(signals))cancelSignal(s);signals.clear();
        clearPresentation(immediate);extension=null;key=null;received=false;retried=false;
        if(focused)host.returnToTile(previousKey);
    }
    private void pruneImages(){
        // Only live view bitmaps count as retained. Foreground refresh must not accumulate old snapshots.
        java.util.Set<Bitmap> live=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        collectImages(content,live);collectImages(backgrounds,live);images.retainAll(live);
        imageBytes=0;for(Bitmap bitmap:images)imageBytes+=CompatApi.allocationBytes(bitmap);
    }
    private void collectImages(View view,java.util.Set<Bitmap> live){
        if(view instanceof ImageView){android.graphics.drawable.Drawable drawable=((ImageView)view).getDrawable();
            if(drawable instanceof android.graphics.drawable.BitmapDrawable)live.add(((android.graphics.drawable.BitmapDrawable)drawable).getBitmap());}
        else if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collectImages(((ViewGroup)view).getChildAt(i),live);
    }
    void trimImages(){
        acceptingImages=false;
        for(android.os.CancellationSignal signal:new ArrayList<>(signals))cancelSignal(signal);signals.clear();
        clearImages(content);
        for(int i=backgrounds.getChildCount()-1;i>=0;i--)removeImage((ImageView)backgrounds.getChildAt(i));
        backgroundKey=null;images.clear();imageBytes=0;
    }
    private void clearImages(View view){
        if(view instanceof ImageView)((ImageView)view).setImageDrawable(null);
        else if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)clearImages(((ViewGroup)view).getChildAt(i));
    }
    void close(){stop(true);disposed=true;root.removeView(content);root.removeView(backgrounds);}
}
