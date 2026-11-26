package com.gentv.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Worker-thread catalogue and bounded image cache for explicit wallpaper browsing. */
final class OnlineWallpaperGallery {
    static final int MAX_IMAGE = 8 * 1024 * 1024;
    private final Context context;
    private final File directory;
    private final String base;
    volatile boolean closed;
    private volatile HttpURLConnection connection;

    static final class Entry {
        final String url, thumbnail, title;
        Entry(String url, String thumbnail, String title) {
            this.url = url; this.thumbnail = thumbnail; this.title = title;
        }
    }

    OnlineWallpaperGallery(Context context) {
        this.context = context.getApplicationContext();
        base = TvCloud.BASE;
        directory = new File(context.getCacheDir(), "online-wallpaper-gallery");
        directory.mkdirs();
    }

    static boolean allowed(String value, String base) {
        try {
            URI uri = new URI(value), root = new URI(base);
            if (base.isEmpty() || value.length() > 2048 || !"https".equals(uri.getScheme())
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || !root.getHost().equalsIgnoreCase(uri.getHost()) || root.getPort() != uri.getPort()) return false;
            String prefix = root.getPath() + "wallpapers/", path = uri.getPath();
            if (!path.startsWith(prefix)) return false;
            String file = path.substring(prefix.length());
            return !file.isEmpty() && !file.contains("/") && !file.contains("\\")
                    && !file.equals(".") && !file.equals("..");
        } catch (Exception invalid) { return false; }
    }

    private List<Entry> parse(byte[] bytes) throws Exception {
        JSONArray array = new JSONObject(new String(bytes, Charset.forName("UTF-8"))).getJSONArray("wallpapers");
        if (array.length() > 100) throw new IOException("Too many wallpapers");
        List<Entry> entries = new ArrayList<>();
        java.util.Set<String> urls = new java.util.HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String url = item.getString("url"), thumb = item.optString("thumbnailUrl", url);
            if (!allowed(url, base) || !allowed(thumb, base)) continue;
            String title = item.optString("title", "");
            if (urls.add(url)) entries.add(new Entry(url, thumb, title.length() > 80 ? title.substring(0, 80) : title));
        }
        return entries;
    }

    List<Entry> load() throws Exception {
        if (base.isEmpty()) throw new IOException("Wallpaper service is not configured");
        AtomicFile manifest = new AtomicFile(new File(directory, "catalogue.json"));
        try {
            byte[] bytes = fetch(base + "wallpaper", 65536, true);
            List<Entry> entries = parse(bytes);
            write(manifest, bytes);
            return entries;
        } catch (Exception unavailable) {
            try {
                byte[] saved = manifest.readFully();
                if (saved.length > 65536) throw new IOException("Catalogue too large");
                return parse(saved);
            } catch (Exception absent) { throw unavailable; }
        }
    }

    private byte[] fetch(String url, int limit, boolean manifest) throws Exception {
        if (closed) throw new IOException("Gallery closed");
        if (!manifest && !allowed(url, base)) throw new IOException("Unexpected wallpaper URL");
        HttpURLConnection c = TvCloud.open(url);
        c.setRequestProperty("Accept-Encoding", "identity");
        connection = c;
        try {
            if (c.getResponseCode() != 200) throw new IOException("Wallpaper HTTP " + c.getResponseCode());
            if (CompatApi.contentLength(c) > limit) throw new IOException("Wallpaper too large");
            byte[] data = TvCloud.read(c.getInputStream(), limit);
            long expected = CompatApi.contentLength(c);
            if (expected >= 0 && expected != data.length) throw new IOException("Incomplete wallpaper");
            if (closed) throw new IOException("Gallery closed");
            return data;
        } finally { connection = null; c.disconnect(); }
    }

    private File image(String url) throws Exception {
        if (!allowed(url, base)) throw new IOException("Unexpected wallpaper URL");
        String hash = UpdatePackage.hex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(Charset.forName("UTF-8"))));
        File file = new File(directory, hash + ".image");
        if (file.isFile() && file.length() > 0 && file.length() <= MAX_IMAGE) {
            file.setLastModified(System.currentTimeMillis());
            return file;
        }
        byte[] data = fetch(url, MAX_IMAGE, false);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (!WallpaperSize.accepted(bounds.outWidth, bounds.outHeight)) throw new IOException("Invalid wallpaper image");
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".image"));
        if (files != null) {
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
            long size = data.length;
            for (File cached : files) size += cached.length();
            for (File cached : files) if (size > 64L * 1024 * 1024) {
                long length = cached.length();
                if (cached.delete()) size -= length;
            }
        }
        write(new AtomicFile(file), data);
        return file;
    }

    Bitmap preview(Entry entry, int width, int height, boolean thumbnail) throws Exception {
        File file = image(thumbnail ? entry.thumbnail : entry.url);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (!WallpaperSize.accepted(options.outWidth, options.outHeight)) throw new IOException("Invalid cached image");
        options.inSampleSize = WallpaperSize.sampleSize(options.outWidth, options.outHeight, width, height);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    String savedId(Entry entry) { return context.getSharedPreferences("online-wallpaper-gallery", 0).getString(entry.url, ""); }

    String select(Entry entry) throws Exception {
        String id = savedId(entry);
        if (!UserWallpaper.isImage(id) || !UserWallpaper.image(context, id).isFile()) {
            id = UserWallpaper.importStaged(context, image(entry.url), "image/*");
            context.getSharedPreferences("online-wallpaper-gallery", 0).edit().putString(entry.url, id).apply();
        }
        return id;
    }

    void close() { closed = true; HttpURLConnection c = connection; if (c != null) c.disconnect(); }

    private static void write(AtomicFile target, byte[] bytes) throws IOException {
        FileOutputStream stream = target.startWrite();
        try { stream.write(bytes); target.finishWrite(stream); }
        catch (IOException error) { target.failWrite(stream); throw error; }
    }
}
