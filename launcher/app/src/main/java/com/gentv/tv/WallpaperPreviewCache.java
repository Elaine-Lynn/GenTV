package com.gentv.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import android.util.LruCache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * The wallpaper gallery's poster pictures, kept across visits: decoded bitmaps in memory for the life of the
 * process, and the downloaded files on disk. A cached poster shows at once; at most once a day it is checked
 * against the server with a conditional request and downloaded again only when it changed.
 * Disk and network methods run on a worker thread.
 */
final class WallpaperPreviewCache {
    static final long LIMIT = 32L * 1024 * 1024;
    static final long REVALIDATE_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static LruCache<String, Bitmap> memory;

    private WallpaperPreviewCache() { }

    private static synchronized LruCache<String, Bitmap> memory() {
        if (memory == null) {
            long heap = Runtime.getRuntime().maxMemory();
            int size = (int) Math.min(12L * 1024 * 1024, Math.max(2L * 1024 * 1024, heap / 16));
            memory = new LruCache<String, Bitmap>(size) {
                @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
            };
        }
        return memory;
    }

    /** A decoded picture already in memory, by key (a gallery asset path or a local key). */
    static Bitmap remembered(String key) {
        Bitmap bitmap = memory().get(key);
        return bitmap != null && !bitmap.isRecycled() ? bitmap : null;
    }

    static void remember(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) memory().put(key, bitmap);
    }

    /** The poster saved on disk for a gallery asset path, decoded and remembered; null when there is none. */
    static Bitmap cached(Context context, String path, int maxSide) {
        File file = file(context, path);
        if (!file.isFile()) return null;
        Bitmap bitmap = decode(file.getPath(), maxSide);
        if (bitmap == null) { delete(file); return null; }
        remember(path, bitmap);
        return bitmap;
    }

    /** Whether the saved poster is missing or was last confirmed with the server over a day ago. */
    static boolean stale(Context context, String path) {
        File file = file(context, path);
        return !file.isFile() || due(file.lastModified(), System.currentTimeMillis());
    }

    static boolean due(long checkedAt, long now) {
        return checkedAt <= 0 || now - checkedAt >= REVALIDATE_MS || now < checkedAt;
    }

    /**
     * Downloads the poster, conditionally when one is saved. Returns the new picture when it was downloaded,
     * or null when the saved one is still current or the request failed (the saved one stays).
     */
    static Bitmap refresh(Context context, String path, String url, int maxSide) {
        File file = file(context, path);
        File tags = new File(file.getPath() + ".tag");
        boolean saved = file.isFile();
        HttpURLConnection connection = null;
        try {
            connection = TvCloud.open(url);
            String[] validators = saved ? readTags(tags) : new String[]{"", ""};
            if (!validators[0].isEmpty()) connection.setRequestProperty("If-None-Match", validators[0]);
            else if (!validators[1].isEmpty()) connection.setRequestProperty("If-Modified-Since", validators[1]);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED && saved) {
                file.setLastModified(System.currentTimeMillis());
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) return null;
            byte[] data = TvCloud.read(connection.getInputStream(), MAX_BYTES);
            Bitmap bitmap = decode(data, maxSide);
            if (bitmap == null) return null;
            File directory = file.getParentFile();
            if (directory != null && (directory.isDirectory() || directory.mkdirs())) {
                write(file, data);
                write(tags, (header(connection, "ETag") + "\n" + header(connection, "Last-Modified")).getBytes(UTF_8));
                prune(directory, file);
            }
            remember(path, bitmap);
            return bitmap;
        } catch (IOException | RuntimeException | OutOfMemoryError failure) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** {ETag, Last-Modified} saved with a poster, each empty when absent. */
    static String[] parseTags(String saved) {
        String[] lines = saved == null ? new String[0] : saved.split("\n", -1);
        return new String[]{lines.length > 0 ? lines[0].trim() : "", lines.length > 1 ? lines[1].trim() : ""};
    }

    /** A stable, path-safe file name for a gallery asset path. */
    static String fileName(String path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(path.getBytes(UTF_8));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 16; i++) name.append(String.format(Locale.ROOT, "%02x", digest[i] & 255));
            return name.append(".img").toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Over the limit, the posters not shown or confirmed for the longest go first; {@code keep} stays. */
    private static void prune(File directory, File keep) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".img"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= LIMIT) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= LIMIT) break;
            if (file.equals(keep)) continue;
            total -= file.length();
            delete(file);
        }
    }

    private static File file(Context context, String path) {
        return new File(new File(context.getCacheDir(), "gallery_previews"), fileName(path));
    }

    private static void delete(File file) {
        new AtomicFile(file).delete();
        new AtomicFile(new File(file.getPath() + ".tag")).delete();
    }

    private static String[] readTags(File tags) {
        try {
            return parseTags(new String(new AtomicFile(tags).readFully(), UTF_8));
        } catch (FileNotFoundException absent) {
            return new String[]{"", ""};
        } catch (IOException unreadable) {
            return new String[]{"", ""};
        }
    }

    private static String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value.replace('\n', ' ').trim();
    }

    private static void write(File target, byte[] data) throws IOException {
        AtomicFile file = new AtomicFile(target);
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data);
            file.finishWrite(out);
        } catch (IOException failure) {
            if (out != null) file.failWrite(out);
            throw failure;
        }
    }

    private static int sample(BitmapFactory.Options bounds, int maxSide) {
        int sample = 1;
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2;
        return sample;
    }

    private static Bitmap decode(String path, int maxSide) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample(options, maxSide);
            // Posters are opaque JPEGs: 16-bit colour halves their memory and the texture upload on first draw.
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inDither = true;
            return BitmapFactory.decodeFile(path, options);
        } catch (RuntimeException | OutOfMemoryError failure) {
            return null;
        }
    }

    private static Bitmap decode(byte[] data, int maxSide) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample(options, maxSide);
            // Posters are opaque JPEGs: 16-bit colour halves their memory and the texture upload on first draw.
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inDither = true;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (RuntimeException | OutOfMemoryError failure) {
            return null;
        }
    }
}
