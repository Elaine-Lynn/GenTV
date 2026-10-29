package com.gentv.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runs against Android storage and PackageManager, without changing Home or system settings. */
public final class DesktopIntegration extends Instrumentation {
    private int checks;
    private boolean cloudUiOnly;
    private boolean resetCloudPolicy;
    private boolean recoveryFixture;
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private List<String> originalOrder(JSONArray order) { List<String> result=new ArrayList<>();for(int i=0;i<order.length();i++)result.add(order.optString(i));return result; }
    private android.graphics.Bitmap renderNetwork(boolean ethernet,boolean online,boolean validated,int level) {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(56,56,android.graphics.Bitmap.Config.ARGB_8888);
        NetworkStatusDrawable glyph=new NetworkStatusDrawable(2,ethernet,online,validated,level);
        glyph.setBounds(0,0,56,56);glyph.draw(new android.graphics.Canvas(bitmap));return bitmap;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); cloudUiOnly=args!=null && "true".equals(args.getString("cloud_ui_only")); resetCloudPolicy=args!=null && "true".equals(args.getString("reset_cloud_policy")); recoveryFixture=args!=null && "true".equals(args.getString("recovery_fixture")); start(); }
    private android.view.View findCloudText(android.view.View view,CharSequence text) {
        if(view instanceof android.widget.TextView && text.equals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof android.view.ViewGroup) {android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=findCloudText(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private void runCloudUi(Runnable action) {
        Throwable[] failure=new Throwable[1];
        runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
    @SuppressWarnings("deprecation")
    private void checkPendingInstallRecovery() throws Exception {
        android.content.Context ctx=getTargetContext();
        java.io.File fixture=new java.io.File(ctx.getExternalFilesDir(null),"verified-update-fixture.apk");
        check(fixture.isFile(),"recovery fixture present");
        android.content.SharedPreferences prefs=TvCloud.prefs(ctx);
        String oldHash=prefs.getString("pendingHash",null); long oldVersion=prefs.getLong("pendingVersion",0);
        java.io.File ready=new java.io.File(ctx.getCacheDir(),"updates/gentv.apk");
        java.io.File saved=new java.io.File(ready.getPath()+".recovery-test-backup");
        ready.getParentFile().mkdirs();
        boolean hadFile=ready.exists();
        if(hadFile && !ready.renameTo(saved)) throw new java.io.IOException("Cannot preserve cached update");
        android.app.Activity recreated=null;
        try {
            java.nio.file.Files.copy(fixture.toPath(),ready.toPath());
            String hash=UpdatePackage.hex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(ready.toPath())));
            long version=ctx.getPackageManager().getPackageArchiveInfo(ready.getPath(),0).versionCode;
            check(prefs.edit().putString("pendingHash",hash).putLong("pendingVersion",version).commit(),"pending state persisted");
            check(UpdatePackage.verified(ctx).equals(ready),"cached signed APK accepted after reload");
            recreated=startActivitySync(new android.content.Intent(ctx,CloudSettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            android.app.Activity screen=recreated;
            runCloudUi(()->check(findCloudText(screen.findViewById(android.R.id.content),ctx.getString(R.string.cloud_install))!=null,"new activity restores Install action"));
            runCloudUi(()->screen.finish());recreated=null;
            try(java.io.RandomAccessFile corrupt=new java.io.RandomAccessFile(ready,"rw")){corrupt.writeByte(0);}
            try {UpdatePackage.verified(ctx);throw new AssertionError("Corrupted cached APK accepted");}catch(java.io.IOException expected){checks++;}
        } finally {
            if(recreated!=null){android.app.Activity screen=recreated;runCloudUi(()->screen.finish());}
            ready.delete();if(hadFile && !saved.renameTo(ready)) throw new java.io.IOException("Cannot restore cached update");
            android.content.SharedPreferences.Editor edit=prefs.edit().putLong("pendingVersion",oldVersion);
            if(oldHash==null)edit.remove("pendingHash");else edit.putString("pendingHash",oldHash);edit.commit();
        }
    }
    private void checkCloudUi() {
        Bundle result=new Bundle(); int status=0;
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("gentv-cloud",android.content.Context.MODE_PRIVATE);
        long original=resetCloudPolicy?0:prefs.getLong("requiredVersion",0);
        long originalUntil=prefs.getLong("requiredUntil",0);
        android.app.Activity[] opened=new android.app.Activity[1];
        try {
            if(!(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"))) throw new IllegalStateException("Emulator only");
            if(recoveryFixture) checkPendingInstallRecovery();
            android.content.Context ctx=getTargetContext();
            JSONObject metadata=new JSONObject().put("versionCode",BuildConfig.VERSION_CODE+1).put("versionName","test")
                    .put("packageName",ctx.getPackageName()).put("url",TvCloud.BASE+"releases/test.apk")
                    .put("sha256",new String(new char[64]).replace('\0','a')).put("signingCertificateSha256",UpdatePackage.certificate(ctx));
            TvCloud.validate(metadata,ctx);check(true,"valid signed metadata");
            metadata.put("packageName","wrong.package");
            try {TvCloud.validate(metadata,ctx);throw new AssertionError("Wrong package accepted");}catch(java.io.IOException expected){checks++;}
            metadata.put("packageName",ctx.getPackageName()).put("url",TvCloud.BASE+"../unsafe.apk");
            try {TvCloud.validate(metadata,ctx);throw new AssertionError("Unsafe URL accepted");}catch(java.io.IOException expected){checks++;}
            metadata.put("url",TvCloud.BASE+"releases/test.apk").put("signingCertificateSha256","invalid");
            try {TvCloud.validate(metadata,ctx);throw new AssertionError("Wrong signer accepted");}catch(java.io.IOException expected){checks++;}
            prefs.edit().putLong("requiredVersion",BuildConfig.VERSION_CODE+1).putLong("requiredUntil",1).commit();
            check(!TvCloud.required(ctx),"expired mandatory policy releases offline TV");
            try {TvCloud.remember(ctx,metadata);throw new AssertionError("Malformed policy stored");}catch(java.io.IOException expected){checks++;}
            check(prefs.getLong("requiredUntil",0)==1,"bad metadata cannot extend mandatory lease");
            prefs.edit().putLong("requiredVersion",BuildConfig.VERSION_CODE+1L).putLong("requiredUntil",System.currentTimeMillis()+60000).commit();
            opened[0]=startActivitySync(new android.content.Intent(getTargetContext(),CloudSettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            runCloudUi(()->{
                try {
                    android.view.View later=findCloudText(opened[0].findViewById(android.R.id.content),"Later");
                    check(later.getVisibility()==android.view.View.GONE,"mandatory Later hidden");
                    opened[0].onBackPressed();check(!opened[0].isFinishing(),"mandatory Back blocked");
                    ((android.view.ViewGroup)opened[0].findViewById(android.R.id.content)).getChildAt(0).performClick();
                    check(!opened[0].isFinishing(),"mandatory outside click blocked");
                } catch(Exception e) {throw new AssertionError(e);}
            });
            waitForIdleSync();
            android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();
            java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),"mandatory-update-test.png");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)) {shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            prefs.edit().putLong("requiredVersion",0).commit();
            runCloudUi(()->opened[0].finish());opened[0]=null;
            opened[0]=startActivitySync(new android.content.Intent(getTargetContext(),CloudSettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            runCloudUi(()->{
                try {
                    check(findCloudText(opened[0].findViewById(android.R.id.content),"Later").getVisibility()==android.view.View.VISIBLE,"optional Later visible");
                    findCloudText(opened[0].findViewById(android.R.id.content),"Later").performClick();check(opened[0].isFinishing(),"optional Later closes");
                }catch(Exception e){throw new AssertionError(e);}
            });
            status=-1;result.putString("result","PASS "+checks+" mandatory/optional UI checks");
        }catch(Throwable e){result.putString("error",e.toString());}
        finally {prefs.edit().putLong("requiredVersion",original).putLong("requiredUntil",originalUntil).commit();if(opened[0]!=null)runCloudUi(()->opened[0].finish());}
        finish(status,result);
    }

    @Override public void onStart() {
        if(cloudUiOnly) {checkCloudUi();return;}
        Bundle result = new Bundle();
        JSONObject original = null;
        int status = -1;
        try {
            android.content.Context ctx = getTargetContext();
            if (!(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"))) throw new IllegalStateException("Emulator-only test");
            original = DesktopStore.snapshot(ctx).getJSONObject("config");
            JSONObject baseline = DesktopStore.snapshot(ctx);
            check(baseline.getJSONObject("config").getInt("schema") == 2, "schema");
            check(baseline.getJSONArray("apps").length() > 0, "catalog");
            String first = baseline.getJSONArray("apps").getJSONObject(0).getString("id");
            JSONObject profile = new JSONObject(original.toString());
            profile.getJSONObject("names").put(first, "Integration name");
            profile.put("effects", "smooth");
            JSONObject saved = DesktopStore.save(ctx, profile, baseline.getInt("revision"));
            check(saved.getInt("revision") > baseline.getInt("revision"), "revision");
            check(DesktopStore.name(ctx, first, "missing").equals("Integration name"), "name");
            check(saved.getBoolean("smooth"), "smooth");
            int revision = saved.getInt("revision");
            try { DesktopStore.save(ctx, profile, baseline.getInt("revision")); throw new AssertionError("stale accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(DesktopStore.revision(ctx) == revision, "stale mutated state");
            JSONObject invalid = new JSONObject(profile.toString()).put("unexpected", true);
            try { DesktopStore.save(ctx, invalid, revision); throw new AssertionError("unknown accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(DesktopStore.revision(ctx) == revision, "invalid mutated state");
            JSONObject badInput=new JSONObject(profile.toString()).put("pinnedSources",new JSONArray().put("missing-input"));
            try { DesktopStore.save(ctx,badInput,revision); throw new AssertionError("unknown input accepted"); }
            catch(IllegalArgumentException expected) { checks++; }
            JSONObject badTarget=new JSONObject(profile.toString()).put("sourceTargets",new JSONObject().put("missing-input","missing-target"));
            try { DesktopStore.save(ctx,badTarget,revision); throw new AssertionError("unknown target accepted"); }
            catch(IllegalArgumentException expected) { checks++; }
            check(DesktopStore.revision(ctx)==revision,"bad input mutated state");
            JSONObject undo = DesktopStore.undo(ctx, revision);
            check(undo.getJSONObject("config").toString().equals(original.toString()), "exact undo");
            check(!undo.getBoolean("canUndo"), "undo is single use");
            String pkg = android.content.ComponentName.unflattenFromString(first).getPackageName();
            JSONObject remoteHide = new JSONObject(original.toString());
            JSONArray hiddenKeys = remoteHide.getJSONArray("hidden");
            java.util.Set<String> hiddenSet = new java.util.HashSet<>();
            for(int i=0;i<hiddenKeys.length();i++) hiddenSet.add(hiddenKeys.getString(i));
            for(String key:DesktopStore.catalog(ctx).keySet()) if(android.content.ComponentName.unflattenFromString(key).getPackageName().equals(pkg)) hiddenSet.add(key);
            remoteHide.put("hidden", new JSONArray(new ArrayList<>(hiddenSet)));
            DesktopStore.save(ctx,remoteHide,DesktopStore.revision(ctx));
            check(DesktopStore.libraryVisibilityReady(ctx), "derived library state");
            check(LibraryApps.loadRemoved(ctx).contains(pkg), "phone-hidden app restorable without a catalogue refresh");
            check(LibraryApps.save(ctx,pkg,true), "remote restores phone-hidden app");
            check(!DesktopStore.hidden(ctx,first), "restored component visible");
            DesktopStore.save(ctx,original,DesktopStore.revision(ctx));
            int beforeLocal = DesktopStore.revision(ctx);
            check(LibraryApps.save(ctx, pkg, false), "local hide");
            check(DesktopStore.hidden(ctx, first), "local hide visibility");
            check(DesktopStore.revision(ctx) > beforeLocal, "local invalidates draft");
            check(LibraryApps.loadRemoved(ctx).contains(pkg), "library can restore hidden app");
            try { DesktopStore.save(ctx, profile, beforeLocal); throw new AssertionError("local draft overwrite"); }
            catch (IllegalArgumentException expected) { checks++; }
            DesktopStore.undo(ctx, DesktopStore.revision(ctx));
            check(!DesktopStore.hidden(ctx, first) || original.getJSONArray("hidden").toString().contains(first), "local undo");
            DesktopStore.editing(true);
            try { DesktopStore.save(ctx, profile, DesktopStore.revision(ctx)); throw new AssertionError("editing overwrite"); }
            catch (IllegalStateException expected) { checks++; }
            finally { DesktopStore.editing(false); }
            List<String> keys = new ArrayList<>(DesktopStore.catalog(ctx).keySet());
            Collections.reverse(keys);
            int beforeOrder = DesktopStore.revision(ctx);
            Config cfg = Config.load(ctx);
            check(Order.save(Order.file(ctx), new Order(Order.dockKey(cfg.dockApps), keys)), "remote order");
            check(DesktopStore.revision(ctx) > beforeOrder, "remote order invalidates phone");
            List<String> readOrder=Order.load(Order.file(ctx)).keys;
            check(readOrder.size()>=keys.size() && readOrder.subList(0,keys.size()).equals(keys), "order readback");
            check(readOrder.containsAll(original.getJSONArray("order").length()==0 ? Collections.emptyList() : originalOrder(original.getJSONArray("order"))), "missing order keys retained");
            DesktopStore.undo(ctx, DesktopStore.revision(ctx));
            JSONObject dup = new JSONObject(profile.toString());
            dup.put("order", new JSONArray().put(first).put(first));
            try { DesktopStore.validate(dup); throw new AssertionError("duplicate accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(DesktopStore.rename(ctx, first, "Local rename"), "remote rename");
            check(DesktopStore.name(ctx, first, "missing").equals("Local rename"), "remote name readback");
            DesktopStore.undo(ctx, DesktopStore.revision(ctx));
            JSONObject extras = DesktopStore.snapshot(ctx).getJSONObject("config");
            String folderKey=DesktopExtras.newFolderKey();
            extras.getJSONObject("folders").put(folderKey,new JSONObject().put("name","Video").put("apps",new JSONArray().put(first).put("com.missing.app/.Main")));
            extras.getJSONArray("order").put(folderKey);
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(320,180,android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff225588);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,80,bytes);bitmap.recycle();
            String picture=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            extras.getJSONObject("images").put(first,picture);
            JSONObject extraSaved=DesktopStore.save(ctx,extras,DesktopStore.revision(ctx));
            check(extraSaved.getJSONObject("config").getJSONObject("folders").has(folderKey),"folder persisted");
            check(DesktopExtras.image(ctx,first)!=null,"custom image decoded");
            check(DesktopExtras.members(extras.getJSONObject("folders")).contains("com.missing.app/.Main"),"missing folder app retained");
            JSONObject badFolder=new JSONObject(extras.toString());
            badFolder.getJSONObject("folders").put(DesktopExtras.newFolderKey(),new JSONObject().put("name","Duplicate").put("apps",new JSONArray().put(first)));
            try { DesktopStore.validate(badFolder);throw new AssertionError("duplicate folder app accepted"); }catch(IllegalArgumentException expected){checks++;}
            badFolder=new JSONObject(extras.toString());badFolder.getJSONObject("folders").getJSONObject(folderKey).put("apps",new JSONArray().put(folderKey));
            try { DesktopStore.validate(badFolder);throw new AssertionError("nested folder accepted"); }catch(IllegalArgumentException expected){checks++;}
            JSONObject badPicture=new JSONObject(extras.toString());badPicture.getJSONObject("images").put(first,"bm90LWltYWdl");
            try { DesktopStore.validate(badPicture);throw new AssertionError("bad image accepted"); }catch(IllegalArgumentException expected){checks++;}
            JSONObject oldClient=new JSONObject(extras.toString());oldClient.remove("folders");oldClient.remove("images");
            try { DesktopStore.save(ctx,oldClient,DesktopStore.revision(ctx));throw new AssertionError("old client overwrote extras"); }catch(IllegalArgumentException expected){checks++;}
            JSONObject dissolved=new JSONObject(extras.toString());dissolved.getJSONObject("folders").remove(folderKey);
            JSONArray remaining=new JSONArray();for(int i=0;i<dissolved.getJSONArray("order").length();i++){String key=dissolved.getJSONArray("order").getString(i);if(!key.equals(folderKey))remaining.put(key);}
            dissolved.put("order",remaining);
            DesktopStore.save(ctx,dissolved,DesktopStore.revision(ctx));
            check(!DesktopStore.snapshot(ctx).getJSONObject("config").getJSONArray("order").toString().contains(folderKey),"dissolve removes synthetic order key");
            JSONObject extraUndo=DesktopStore.undo(ctx,DesktopStore.revision(ctx));
            check(extraUndo.getJSONObject("config").getJSONObject("folders").has(folderKey),"undo restores folder");
            check(extraUndo.getJSONObject("config").getJSONObject("images").getString(first).equals(picture),"undo restores image");
            check(RemoteWallpaper.INTERVAL_MS==120000L,"wallpaper two minute interval");
            check(RemoteWallpaper.parse("{\"wallpapers\":[]}",false).isEmpty(),"empty wallpaper feed");
            check(RemoteWallpaper.parse("{\"wallpapers\":[{\"url\":\"https://gentv.me/a.jpg\"},{\"url\":\"https://gentv.me/a.jpg\"}]}",false).size()==1,"wallpaper feed deduplicated");
            try {RemoteWallpaper.parse("{\"wallpapers\":[{\"url\":\"http://example.com/a.jpg\"}]}",false);throw new AssertionError("cleartext wallpaper accepted");}catch(IllegalArgumentException expected){checks++;}
            try {RemoteWallpaper.parse("{\"wallpapers\":[{\"url\":\"https://user:pass@example.com/a.jpg\"}]}",false);throw new AssertionError("wallpaper credentials accepted");}catch(IllegalArgumentException expected){checks++;}
            check(RemoteWallpaper.parse("{\"wallpapers\":[{\"url\":\"http://127.0.0.1:18765/a.jpg\"}]}",true).size()==1,"debug loopback fixture");
            java.io.File cacheDir=new java.io.File(ctx.getCacheDir(),"wallpaper-cache-integration");
            WallpaperCache disk=new WallpaperCache(cacheDir,"https://api.gentv.app/tv/wallpaper");
            String url="https://gentv.me/one.jpg?v=1",newUrl="https://gentv.me/one.jpg?v=2";
            disk.write(url,new byte[]{1,2,3});disk.saveState(java.util.Arrays.asList(url),123,url);
            WallpaperCache restarted=new WallpaperCache(cacheDir,"https://api.gentv.app/tv/wallpaper");
            check(java.util.Arrays.equals(restarted.read(url),new byte[]{1,2,3}),"image survives process recreation");
            check(restarted.state().getString("selected").equals(url),"playlist selection persisted");
            check(restarted.read(newUrl)==null,"versioned URL invalidates image cache");
            restarted.prune(java.util.Collections.emptyList());
            check(restarted.read(url)==null,"removed image cache pruned");
            new android.util.AtomicFile(new java.io.File(cacheDir,"playlist.json")).delete();cacheDir.delete();
            android.graphics.Bitmap weak=renderNetwork(false,true,true,1),strong=renderNetwork(false,true,true,4);
            android.graphics.Bitmap wired=renderNetwork(true,true,true,4),offline=renderNetwork(false,false,false,0);
            check(!weak.sameAs(strong),"Wi-Fi strength changes glyph");
            check(!wired.sameAs(strong),"Ethernet has distinct glyph");
            check(!offline.sameAs(strong),"offline has distinct glyph");
            weak.recycle();strong.recycle();wired.recycle();offline.recycle();
            java.util.List<android.content.pm.ResolveInfo> installed=ctx.getPackageManager().queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER),0);
            android.content.pm.ResolveInfo template=installed.get(0);
            java.util.List<android.content.pm.ResolveInfo> fixture=new java.util.ArrayList<>();
            for(int i=0;i<31;i++) {
                android.content.pm.ResolveInfo info=new android.content.pm.ResolveInfo(template);
                info.activityInfo=new android.content.pm.ActivityInfo(template.activityInfo);
                info.activityInfo.packageName=String.format(java.util.Locale.ROOT,"com.gentv.fixture.p%02d",i);
                info.activityInfo.name=info.activityInfo.packageName+".Main";
                info.activityInfo.nonLocalizedLabel=String.format(java.util.Locale.ROOT,"Fixture %02d",i);
                info.activityInfo.exported=true;fixture.add(info);
            }
            java.io.File moreFixture=new java.io.File(ctx.getCacheDir(),"more-apps-integration");moreFixture.mkdirs();
            android.content.Context isolated=new android.content.ContextWrapper(ctx) {
                @Override public java.io.File getFilesDir(){return moreFixture;}
            };
            AppAdapter limited=new AppAdapter(isolated,null,5,Config.load(isolated));
            limited.reload(java.util.Collections.emptyList(),fixture.subList(0,30));
            check(limited.appCount()==30 && !limited.hasOverflow() && !limited.isLibraryAt(30),"thirty apps hide More APP: "+limited.appCount()+"/"+limited.hasOverflow());
            limited.reload(java.util.Collections.emptyList(),fixture);
            check(limited.appCount()==30 && limited.hasOverflow() && limited.isLibraryAt(30),"thirty-one apps show More APP");
            check(MoreApps.missing(fixture,app -> app.activityInfo.packageName,limited::containsPackage).size()==1,"overflow catalog excludes every visible app");
            check(limited.movableKeys().size()==31,"reorder retains overflow tail");
            check(limited.containsPackage("com.gentv.fixture.p00") && !limited.containsPackage("com.gentv.fixture.p30"),"catalog predicate only includes visible apps");
            limited.reload(java.util.Collections.emptyList(),fixture.subList(0,29));
            check(limited.appCount()==29 && !limited.hasOverflow(),"removing overflow hides entry");limited.clear();
            for(java.io.File file:moreFixture.listFiles())file.delete();moreFixture.delete();
            result.putString("result", "PASS " + checks + " Android integration checks");

        } catch (Throwable e) {
            status=0; result.putString("error", android.util.Log.getStackTraceString(e));
        } finally {
            if (original != null) try {
                DesktopStore.save(getTargetContext(), original, DesktopStore.revision(getTargetContext()));
            } catch (Exception e) { status=0; result.putString("restoreError", android.util.Log.getStackTraceString(e)); }
        }
        finish(status,result);
    }
}
