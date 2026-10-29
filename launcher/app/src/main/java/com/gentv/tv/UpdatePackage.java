package com.gentv.tv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.*;
import java.security.MessageDigest;

/** Recheck private cached APKs after permission settings and process recreation. */
final class UpdatePackage {
    static String hex(byte[] bytes) {
        StringBuilder value=new StringBuilder();
        for(byte b:bytes) value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return value.toString();
    }
    @SuppressWarnings("deprecation")
    static String certificate(Context context) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(context.getPackageManager()
                .getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES).signatures[0].toByteArray()));
    }
    @SuppressWarnings("deprecation")
    static File verified(Context context) throws Exception {
        File file=new File(context.getCacheDir(),"updates/gentv.apk");
        if (!file.isFile() || file.length()>8*1024*1024) throw new IOException("Missing update");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1)digest.update(buf,0,n); }
        if (!hex(digest.digest()).equalsIgnoreCase(TvCloud.prefs(context).getString("pendingHash",""))) throw new IOException("Changed update");
        PackageManager pm=context.getPackageManager();
        PackageInfo apk=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES);
        PackageInfo own=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        if(apk==null || !context.getPackageName().equals(apk.packageName) || apk.versionCode<=BuildConfig.VERSION_CODE
                || apk.versionCode!=TvCloud.prefs(context).getLong("pendingVersion",0)
                || apk.signatures==null || !java.util.Arrays.equals(own.signatures,apk.signatures)) throw new IOException("Invalid update package");
        return file;
    }
}
