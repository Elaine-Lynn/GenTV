package com.gentv.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets GenTV Remote write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.gentv.tv.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gentv.tv.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.gentv.tv.theme/config.json
 *   content delete --uri content://com.gentv.tv.theme/wallpapers      (every image in the collection)
 */
public class ThemeProvider extends ContentProvider {

    private File file(Uri uri) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        File dir = Config.dir(getContext());
        if (dir == null || path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(dir, path);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Read through the same permission-guarded provider on TVs that hide Android/data from ADB. */
    @Override
    public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        if (!"read_config".equals(method)) return super.call(method, arg, extras);
        getContext().enforceCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS", "Read launcher configuration");
        try {
            File dir = Config.dir(getContext());
            if (dir == null) throw new java.io.IOException("External files unavailable");
            File config = new File(dir, Config.FILE_NAME);
            if (config.length() > 1024 * 1024) throw new java.io.IOException("Config too large");
            byte[] bytes = config.exists() ? java.nio.file.Files.readAllBytes(config.toPath()) : new byte[0];
            android.os.Bundle result = new android.os.Bundle();
            result.putString("config_base64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
            return result;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read launcher configuration", e);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File dir = Config.dir(getContext());
        if (dir != null && ThemeFiles.isCollection(uri.getPathSegments())) {
            File[] images = new File(dir, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name));
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        }
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
