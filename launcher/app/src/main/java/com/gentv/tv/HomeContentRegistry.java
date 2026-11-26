package com.gentv.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discovery and signer-bound permission are independent of the ADB desktop management protocol. */
final class HomeContentRegistry {
    static final String META_SERVICE="com.gentv.tv.home.CONTENT_SERVICE";
    static final String META_VERSION="com.gentv.tv.home.PROTOCOL_MAJOR";
    static final String ACTION="com.gentv.tv.home.BIND_CONTENT";
    static final String PREFS="home-content-consent";
    static final class Extension {
        final ComponentName activity, service;
        final String label, signer;
        final int uid;
        Extension(ComponentName activity,ComponentName service,String label,String signer,int uid) {
            this.activity=activity;this.service=service;this.label=label;this.signer=signer;this.uid=uid;
        }
        String key(){return activity.flattenToShortString();}
        String identity(){return signer+":"+service.flattenToShortString();}
    }
    static Extension find(Context context,String key) {
        if(key==null)return null;
        ComponentName c=ComponentName.unflattenFromString(key);
        if(c==null||c.getPackageName().equals(context.getPackageName()))return null;
        try {
            PackageManager pm=context.getPackageManager();
            ActivityInfo a=pm.getActivityInfo(c,PackageManager.GET_META_DATA);
            if(!a.enabled||!a.exported||!a.applicationInfo.enabled||a.metaData==null
                    ||a.metaData.getInt(META_VERSION,0)!=1)return null;
            // Only a real launchable app activity may publish a content service.
            boolean launchable=false;
            for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
                for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(c.getPackageName()),0))
                    if(r.activityInfo.name.equals(c.getClassName()))launchable=true;
            if(!launchable)return null;
            String declaration=a.metaData.getString(META_SERVICE,"");
            ComponentName service=declaration.contains("/")?ComponentName.unflattenFromString(declaration)
                    :new ComponentName(c.getPackageName(),declaration.startsWith(".")?c.getPackageName()+declaration:declaration);
            if(service==null||!service.getPackageName().equals(c.getPackageName()))return null;
            ServiceInfo s=pm.getServiceInfo(service,0);
            if(!s.enabled||!s.exported||!s.applicationInfo.enabled||s.applicationInfo.uid!=a.applicationInfo.uid
                    ||s.permission!=null&&!s.permission.isEmpty()&&!CompatApi.permissionGranted(context,s.permission))return null;
            String signer=signer(pm,c.getPackageName());
            if(signer==null)return null;
            return new Extension(c,service,a.loadLabel(pm).toString(),signer,a.applicationInfo.uid);
        }catch(Exception|LinkageError invalid){return null;}
    }
    @SuppressWarnings("deprecation")
    private static String signer(PackageManager pm,String pkg) throws Exception {
        Signature[] signatures;
        if(Build.VERSION.SDK_INT>=28){PackageInfo p=pm.getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);
            signatures=p.signingInfo==null?null:p.signingInfo.getApkContentsSigners();}
        else signatures=pm.getPackageInfo(pkg,PackageManager.GET_SIGNATURES).signatures;
        if(signatures==null||signatures.length!=1)return null;
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();
    }
    static List<Extension> all(Context context) {
        Map<String,Extension> result=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo r:context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)) {
                Extension e=find(context,new ComponentName(r.activityInfo.packageName,r.activityInfo.name).flattenToShortString());
                if(e!=null)result.put(e.key(),e);
            }
        return new ArrayList<>(result.values());
    }
    static void packageRemoved(Context c,Intent intent){
        if(!Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())||intent.getBooleanExtra(Intent.EXTRA_REPLACING,false)||intent.getData()==null)return;
        String pkg=intent.getData().getSchemeSpecificPart();SharedPreferences.Editor edit=prefs(c).edit();
        for(String key:prefs(c).getAll().keySet()){
            ComponentName component=ComponentName.unflattenFromString(key.startsWith("channel:") ? key.substring(8,key.lastIndexOf(':')) : key);
            if(component!=null&&component.getPackageName().equals(pkg))edit.remove(key);
        }
        edit.apply();
    }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",true);}
    static boolean allowed(Context c,Extension e){return e.identity().equals(prefs(c).getString(e.key(),""));}
    static String channelKey(Extension e){return "channel:"+e.key()+":"+e.signer;}
    static void allow(Context c,Extension e,boolean allow){
        SharedPreferences.Editor edit=prefs(c).edit();
        if(allow)edit.putString(e.key(),e.identity());else edit.remove(e.key()).remove(channelKey(e));edit.apply();
    }
}
