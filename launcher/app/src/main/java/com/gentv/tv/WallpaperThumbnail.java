package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small on-disk thumbnail of the wallpaper Home shows, for the Control Center's wallpaper row.
 *
 * <p>It is rendered from the wallpaper's own source (never from Home's views, whose bitmaps may be
 * hardware-backed, released under memory pressure or a playing video), keyed by that source's
 * identity and kept in the app's files so the next opening shows it without decoding anything large.
 */
final class WallpaperThumbnail {
    static final int WIDTH = 256;
    static final int HEIGHT = 144;
    /** Bump when the rendering changes so older files are replaced. */
    static final int FORMAT = 2;
    /** Recent thumbnails kept on disk, so switching back to a wallpaper does not render it again. */
    static final int KEEP = 4;
    private static final String DIRECTORY = "wallpaper_thumb";
    private static final String TAG = "GenTV.Thumbnail";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    /** The latest thumbnail in memory. Never recycled: image views may still be drawing it. */
    private static String memoryKey;
    private static Bitmap memory;

    interface Callback { void ready(Bitmap thumbnail); }

    private WallpaperThumbnail() { }

    /** Renders (or loads) the current wallpaper's thumbnail in the background, ahead of the panel opening. */
    static void prepare(Context context) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> ensure(app));
    }

    /**
     * Stores a frame of the video wallpaper Home is playing as the current thumbnail. Many TV decoders hand
     * MediaMetadataRetriever an empty (green or black) frame, so the still saved when the video was imported
     * may be blank, while the frame on screen is always the real picture. Takes ownership of {@code frame}.
     */
    static void fromVideoFrame(Context context, Bitmap frame) {
        if (frame == null) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                Source source = Source.current(app);
                if (!source.video) return;
                Bitmap thumbnail = fit(frame);
                if (thumbnail == null) return;
                String key = source.key();
                File directory = new File(app.getFilesDir(), DIRECTORY);
                save(directory, new File(directory, fileName(key)), thumbnail);
                remember(key, thumbnail);
            } catch (RuntimeException | OutOfMemoryError failure) {
                Log.w(TAG, "Cannot keep the video wallpaper frame", failure);
            } finally {
                frame.recycle();
            }
        });
    }

    /**
     * Shows the last known thumbnail at once when there is one, then delivers the current wallpaper's
     * thumbnail on the main thread if it differs. The callback is skipped once the activity has gone.
     */
    static void load(Activity activity, Callback callback) {
        Bitmap shown = remembered();
        if (shown != null) callback.ready(shown);
        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            Bitmap current = ensure(app);
            if (current == null || current == shown) return;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                callback.ready(current);
            });
        });
    }

    private static synchronized Bitmap remembered() {
        return memory != null && !memory.isRecycled() ? memory : null;
    }

    private static synchronized Bitmap remembered(String key) {
        return key.equals(memoryKey) ? remembered() : null;
    }

    private static synchronized void remember(String key, Bitmap bitmap) {
        memoryKey = key;
        memory = bitmap;
    }

    /** Worker thread: the current wallpaper's thumbnail from memory, disk, or freshly rendered. */
    private static Bitmap ensure(Context context) {
        Source source;
        try {
            source = Source.current(context);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Wallpaper source unavailable", failure);
            source = Source.fallback();
        }
        Bitmap result = ensure(context, source);
        // A video whose still is missing or blank waits for Home's playing frame instead of showing another picture.
        if (result == null && !source.isFallback() && !source.video) result = ensure(context, Source.fallback());
        return result;
    }

    private static Bitmap ensure(Context context, Source source) {
        String key = source.key();
        Bitmap ready = remembered(key);
        if (ready != null) return ready;
        File directory = new File(context.getFilesDir(), DIRECTORY);
        File file = new File(directory, fileName(key));
        if (file.isFile()) {
            Bitmap saved = decodeSaved(file);
            if (saved != null) {
                file.setLastModified(System.currentTimeMillis());
                remember(key, saved);
                return saved;
            }
            new AtomicFile(file).delete();
        }
        Bitmap rendered = render(context, source);
        if (rendered == null) return null;
        save(directory, file, rendered);
        remember(key, rendered);
        return rendered;
    }

    private static Bitmap decodeSaved(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException | OutOfMemoryError failure) {
            return null;
        }
    }

    private static void save(File directory, File file, Bitmap bitmap) {
        if (!directory.isDirectory() && !directory.mkdirs()) return;
        AtomicFile target = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = target.startWrite();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw new IOException("Cannot encode thumbnail");
            target.finishWrite(out);
        } catch (IOException | RuntimeException failure) {
            if (out != null) target.failWrite(out);
            Log.w(TAG, "Cannot save wallpaper thumbnail", failure);
            return;
        }
        File[] saved = directory.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (saved == null || saved.length <= KEEP) return;
        Arrays.sort(saved, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < saved.length; i++) if (!saved[i].equals(file)) new AtomicFile(saved[i]).delete();
    }

    /** Decodes the source at a small sample size and centre-crops it to the thumbnail's 16:9 frame. */
    private static Bitmap render(Context context, Source source) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            source.decode(context, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight, WIDTH, HEIGHT);
            // A software bitmap: hardware ones cannot be drawn into a software canvas or compressed.
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decoded = source.decode(context, options);
            if (decoded == null) return null;
            Bitmap thumbnail = fit(decoded);
            if (thumbnail == null) Log.w(TAG, "Wallpaper source is a blank frame: " + source.key());
            return thumbnail;
        } catch (IOException | RuntimeException | OutOfMemoryError failure) {
            Log.w(TAG, "Cannot render wallpaper thumbnail", failure);
            return null;
        } finally {
            if (decoded != null) decoded.recycle();
        }
    }

    /** Centre-crops a software bitmap into a new thumbnail, or null when the picture is one flat colour. */
    private static Bitmap fit(Bitmap picture) {
        int[] crop = coverCrop(picture.getWidth(), picture.getHeight(), WIDTH, HEIGHT);
        Bitmap thumbnail = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        new Canvas(thumbnail).drawBitmap(picture,
                new Rect(crop[0], crop[1], crop[0] + crop[2], crop[1] + crop[3]),
                new Rect(0, 0, WIDTH, HEIGHT),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        if (!flat(thumbnail)) return thumbnail;
        thumbnail.recycle();
        return null;
    }

    /** Whether a software bitmap is one flat colour (a blank decoder frame), judged on a grid of samples. */
    static boolean flat(Bitmap bitmap) {
        int[] samples = new int[SAMPLE_GRID * SAMPLE_GRID];
        for (int y = 0; y < SAMPLE_GRID; y++) for (int x = 0; x < SAMPLE_GRID; x++)
            samples[y * SAMPLE_GRID + x] = bitmap.getPixel((2 * x + 1) * bitmap.getWidth() / (2 * SAMPLE_GRID),
                    (2 * y + 1) * bitmap.getHeight() / (2 * SAMPLE_GRID));
        return uniform(samples);
    }

    static final int SAMPLE_GRID = 12;
    /** Largest spread of any colour channel across the samples that still counts as one flat colour. */
    static final int FLAT_SPREAD = 12;

    /**
     * Whether sampled ARGB pixels are a single flat colour: the empty green or black frame a TV decoder
     * returns instead of the picture. Real wallpapers, even dark or plain ones, vary more than this.
     */
    static boolean uniform(int[] pixels) {
        if (pixels.length == 0) return true;
        int[] min = {255, 255, 255}, max = {0, 0, 0};
        for (int pixel : pixels) {
            int[] channel = {(pixel >> 16) & 255, (pixel >> 8) & 255, pixel & 255};
            for (int c = 0; c < 3; c++) {
                min[c] = Math.min(min[c], channel[c]);
                max[c] = Math.max(max[c], channel[c]);
            }
        }
        for (int c = 0; c < 3; c++) if (max[c] - min[c] > FLAT_SPREAD) return false;
        return true;
    }

    /** The largest power-of-two sample that still leaves the picture at least {@code width x height}. */
    static int sampleSize(int sourceWidth, int sourceHeight, int width, int height) {
        int sample = 1;
        while (sourceWidth / (sample * 2) >= width && sourceHeight / (sample * 2) >= height) sample *= 2;
        return sample;
    }

    /** {left, top, width, height} of the centred part of the source that fills the frame's aspect ratio. */
    static int[] coverCrop(int sourceWidth, int sourceHeight, int width, int height) {
        if ((long) sourceWidth * height > (long) sourceHeight * width) {
            int cropWidth = Math.max(1, Math.round(sourceHeight * (float) width / height));
            return new int[]{(sourceWidth - cropWidth) / 2, 0, cropWidth, sourceHeight};
        }
        int cropHeight = Math.max(1, Math.round(sourceWidth * (float) height / width));
        return new int[]{0, (sourceHeight - cropHeight) / 2, sourceWidth, cropHeight};
    }

    /** A rotating collection's shown picture by file name, or its first picture before one was shown. */
    static File pick(List<File> files, String shown) {
        if (files == null || files.isEmpty()) return null;
        if (shown != null && !shown.isEmpty()) for (File file : files) if (shown.equals(file.getName())) return file;
        return files.get(0);
    }

    /** Identifies a file source by its path and contents' size and time, a resource by the app build. */
    static String fileKey(File file) {
        return "file:" + file.getAbsolutePath() + ':' + file.lastModified() + ':' + file.length() + ':' + FORMAT;
    }

    static String resourceKey(int resource, int build) {
        return "res:" + resource + ':' + build + ':' + FORMAT;
    }

    /** A stable, path-safe name for a source key. */
    static String fileName(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(Charset.forName("UTF-8")));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 16; i++) name.append(String.format(Locale.ROOT, "%02x", digest[i] & 255));
            return name.append(".jpg").toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Where Home's wallpaper comes from now: a file or a bundled raw resource. */
    static final class Source {
        private final File file;
        private final int resource;
        /** A user video: identified by the video itself, rendered from its imported still (which may be absent). */
        final boolean video;
        private final File identity;

        private Source(File file, int resource) {
            this(file, resource, false, file);
        }

        private Source(File file, int resource, boolean video, File identity) {
            this.file = file;
            this.resource = resource;
            this.video = video;
            this.identity = identity;
        }

        static Source fallback() { return new Source(null, R.raw.wallpaper_default_preview); }

        boolean isFallback() { return !video && file == null && resource == R.raw.wallpaper_default_preview; }

        String key() {
            if (video) return "video:" + fileKey(identity);
            return file != null ? fileKey(file) : resourceKey(resource, BuildConfig.VERSION_CODE);
        }

        Bitmap decode(Context context, BitmapFactory.Options options) throws IOException {
            if (video && (file == null || !file.isFile() || file.length() == 0)) return null;
            if (file != null) return BitmapFactory.decodeFile(file.getPath(), options);
            try (InputStream input = context.getResources().openRawResource(resource)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        }

        /** Mirrors Home: an on-TV pick first, then the online rotation, then the theme, then the default. */
        static Source current(Context context) {
            String selected = BundledWallpapers.selected(context);
            if (UserWallpaper.isImage(selected)) return existing(UserWallpaper.image(context, selected));
            if (UserWallpaper.isVideo(selected))
                return new Source(UserWallpaper.videoPreview(context, selected), 0, true,
                        UserWallpaper.video(context, selected));
            for (int i = 0; i < BundledWallpapers.MOTION_IDS.length; i++)
                if (BundledWallpapers.MOTION_IDS[i].equals(selected))
                    return new Source(null, BundledWallpapers.MOTION_IMAGES[i]);
            for (int i = 1; i < BundledWallpapers.IDS.length; i++)
                if (BundledWallpapers.IDS[i].equals(selected))
                    return new Source(null, BundledWallpapers.IMAGE_PREVIEWS[i]);
            File remote = remote(context);
            if (remote != null) return new Source(remote, 0);
            Config config = Config.load(context);
            File theme = pick(config.wallpaperEnabled ? config.wallpapers : Collections.<File>emptyList(),
                    context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE).getString("image", ""));
            if (theme != null && theme.isFile()) return new Source(theme, 0);
            return fallback();
        }

        private static Source existing(File file) {
            return file != null && file.isFile() && file.length() > 0 ? new Source(file, 0) : fallback();
        }

        /** The online wallpaper RemoteWallpaper last selected, when it is stored locally. */
        private static File remote(Context context) {
            if (!TvCloud.enabled()) return null;
            File directory = new File(context.getFilesDir(), "wallpapers");
            if (!new File(directory, "playlist.json").isFile()) return null;
            try {
                WallpaperCache cache = new WallpaperCache(directory, TvCloud.BASE + "wallpaper");
                String selected = cache.state().optString("selected", "");
                return selected.isEmpty() ? null : cache.stored(selected);
            } catch (Exception unreadable) {
                return null;
            }
        }
    }
}
