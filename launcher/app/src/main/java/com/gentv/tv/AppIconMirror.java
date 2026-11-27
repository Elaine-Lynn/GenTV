package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Tile artwork mirrored on the GenTV server ({@code GET /tv/app-icons}). Only the GenTV API is contacted, with no
 * identifiers; only the artwork of installed apps is downloaded. The list is checked at most once a day (an hour after
 * a failure) and images are kept under their SHA-256, so a tile never shows a half-written or swapped file.
 */
final class AppIconMirror {
    static final long REFRESH_MS = 24L * 60 * 60 * 1000;
    static final long RETRY_MS = 60L * 60 * 1000;
    private static final int MAX_MANIFEST = 512 * 1024;
    static final int MAX_IMAGE = 2 * 1024 * 1024;
    private static final int MAX_DOWNLOADS_PER_RUN = 60;
    static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");
    static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{64}\\.(?:png|jpg|webp)");
    private static final String PREFS = "gentv-app-icons";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** Package name to stored file name; replaced whole, so readers on the main thread need no lock. */
    private static volatile Map<String, String> index;
    private static LruCache<String, Bitmap> bitmaps;
    /** File name to the time its download last failed; only the single IO thread touches it. */
    private static final Map<String, Long> FAILED = new HashMap<>();

    private AppIconMirror() { }

    static File dir(Context ctx) { return new File(ctx.getFilesDir(), "app-icons"); }

    /** The mirrored artwork for a package when it is already on disk, otherwise null. Main thread. */
    static Drawable banner(Context ctx, String pkg) {
        if (!TvCloud.enabled()) return null;
        String name = index(ctx).get(pkg);
        if (name == null) return null;
        Bitmap bitmap = cache(ctx).get(name);
        if (bitmap == null) {
            File file = new File(dir(ctx), name);
            if (!file.isFile()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            // 640x360 artwork; constrained heaps keep a quarter of the pixels, which still fills a tile.
            options.inSampleSize = CompatApi.constrainedHeap(CompatApi.service(ctx, android.app.ActivityManager.class)) ? 2 : 1;
            bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            if (bitmap == null || !AppAdapter.isBannerShaped(bitmap.getWidth(), bitmap.getHeight())) return null;
            cache(ctx).put(name, bitmap);
        }
        return new BitmapDrawable(ctx.getResources(), bitmap);
    }

    /**
     * Brings the list up to date when it is due and fetches artwork that installed apps are missing. Calls
     * {@code changed} on the main thread only when new artwork was stored, so a reload it triggers stays quiet.
     */
    static void refresh(Context context, Collection<String> installed, Runnable changed) {
        if (!TvCloud.enabled() || !RUNNING.compareAndSet(false, true)) return;
        final Context ctx = context.getApplicationContext();
        final Set<String> packages = new HashSet<>(installed);
        final Handler main = new Handler(Looper.getMainLooper());
        TvCloud.IO.execute(() -> {
            boolean stored = false;
            try { stored = sync(ctx, packages); }
            catch (RuntimeException | Error failure) { android.util.Log.w("GenTV.AppIcons", "Icon sync failed", failure); }
            finally { RUNNING.set(false); }
            if (stored) main.post(changed);
        });
    }

    private static boolean sync(Context ctx, Set<String> installed) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File dir = dir(ctx);
        long now = System.currentTimeMillis();
        long next = prefs.getLong("nextCheck", 0);
        // A clock set back must not hold the list for days: treat a far-future check as due.
        if (now >= next || next - now > REFRESH_MS) {
            try {
                Map<String, String> fresh = fetchManifest(prefs);
                if (fresh != null) {
                    writeIndex(dir, fresh);
                    index = Collections.unmodifiableMap(fresh);
                    removeUnlisted(dir, fresh.values());
                }
                prefs.edit().putLong("nextCheck", now + REFRESH_MS).apply();
            } catch (IOException | JSONException failure) {
                prefs.edit().putLong("nextCheck", now + RETRY_MS).apply();
                android.util.Log.w("GenTV.AppIcons", "Icon list unavailable", failure);
            }
        }
        Map<String, String> current = index(ctx);
        boolean stored = false;
        int downloads = 0;
        for (String pkg : installed) {
            String name = current.get(pkg);
            if (name == null || new File(dir, name).isFile()) continue;
            Long failedAt = FAILED.get(name);
            if (failedAt != null && now - failedAt >= 0 && now - failedAt < RETRY_MS) continue;
            if (++downloads > MAX_DOWNLOADS_PER_RUN) break;
            try {
                store(dir, name, download(name));
                FAILED.remove(name);
                stored = true;
            } catch (IOException failure) {
                FAILED.put(name, now);
                android.util.Log.w("GenTV.AppIcons", "Icon download failed for " + pkg, failure);
            }
        }
        return stored;
    }

    /** The new package-to-file list, or null when the server answered 304 Not Modified. */
    private static Map<String, String> fetchManifest(SharedPreferences prefs) throws IOException, JSONException {
        HttpURLConnection c = TvCloud.open(TvCloud.BASE + "app-icons");
        try {
            String etag = prefs.getString("etag", "");
            if (!etag.isEmpty()) c.setRequestProperty("If-None-Match", etag);
            int status = c.getResponseCode();
            if (status == 304) return null;
            if (status != 200) throw new IOException("HTTP " + status);
            Map<String, String> parsed = parse(new String(TvCloud.read(c.getInputStream(), MAX_MANIFEST), Charset.forName("UTF-8")));
            String received = c.getHeaderField("ETag");
            prefs.edit().putString("etag", received == null ? "" : received).apply();
            return parsed;
        } finally { c.disconnect(); }
    }

    /** Valid entries of {@code {"icons":[{"package":..., "url":".../app-icons/<sha256>.png"}]}}; others are skipped. */
    static Map<String, String> parse(String body) throws JSONException {
        JSONArray icons = new JSONObject(body).optJSONArray("icons");
        Map<String, String> result = new HashMap<>();
        if (icons == null) return result;
        for (int i = 0; i < icons.length(); i++) {
            JSONObject icon = icons.optJSONObject(i);
            if (icon == null) continue;
            String pkg = icon.optString("package", ""), url = icon.optString("url", "");
            String name = url.substring(url.lastIndexOf('/') + 1);
            if (PACKAGE.matcher(pkg).matches() && pkg.length() <= 255 && FILE_NAME.matcher(name).matches()
                    && !result.containsKey(pkg)) result.put(pkg, name);
        }
        return result;
    }

    private static byte[] download(String name) throws IOException {
        // Always the GenTV API itself: the file name, not the listed URL, decides what is fetched.
        HttpURLConnection c = TvCloud.open(TvCloud.BASE + "app-icons/" + name);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return TvCloud.read(c.getInputStream(), MAX_IMAGE);
        } finally { c.disconnect(); }
    }

    /** Writes the image only when its bytes hash to its name and decode as a banner-shaped picture. */
    static void store(File dir, String name, byte[] data) throws IOException {
        if (!FILE_NAME.matcher(name).matches() || !name.startsWith(sha256(data) + "."))
            throw new IOException("Icon content does not match its name");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 4096 || bounds.outHeight > 4096
                || !AppAdapter.isBannerShaped(bounds.outWidth, bounds.outHeight))
            throw new IOException("Icon is not a usable banner");
        writeAtomically(new File(dir, name), data);
    }

    static String sha256(byte[] data) {
        try {
            StringBuilder hex = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Map<String, String> index(Context ctx) {
        Map<String, String> loaded = index;
        if (loaded != null) return loaded;
        Map<String, String> read = new HashMap<>();
        File file = new File(dir(ctx), "index.json");
        if (file.isFile() && file.length() <= MAX_MANIFEST) {
            try {
                JSONObject json = new JSONObject(new String(TvCloud.read(new java.io.FileInputStream(file), MAX_MANIFEST), Charset.forName("UTF-8")));
                for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String pkg = it.next(), name = json.optString(pkg, "");
                    if (PACKAGE.matcher(pkg).matches() && FILE_NAME.matcher(name).matches()) read.put(pkg, name);
                }
            } catch (IOException | JSONException ignored) {
                // A damaged index only means the next list check rebuilds it.
            }
        }
        loaded = Collections.unmodifiableMap(read);
        index = loaded;
        return loaded;
    }

    private static void writeIndex(File dir, Map<String, String> entries) throws IOException {
        writeAtomically(new File(dir, "index.json"), new JSONObject(entries).toString().getBytes(Charset.forName("UTF-8")));
    }

    private static void removeUnlisted(File dir, Collection<String> keep) {
        Set<String> wanted = new HashSet<>(keep);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if ((FILE_NAME.matcher(name).matches() && !wanted.contains(name)) || name.endsWith(".part")) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static void writeAtomically(File target, byte[] data) throws IOException {
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File part = new File(dir, target.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!part.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("Cannot store " + target.getName());
        }
    }

    private static synchronized LruCache<String, Bitmap> cache(Context ctx) {
        if (bitmaps == null) {
            boolean constrained = CompatApi.constrainedHeap(CompatApi.service(ctx, android.app.ActivityManager.class));
            bitmaps = new LruCache<String, Bitmap>(constrained ? 2 * 1024 * 1024 : 8 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap value) { return value.getRowBytes() * value.getHeight(); }
            };
        }
        return bitmaps;
    }
}
