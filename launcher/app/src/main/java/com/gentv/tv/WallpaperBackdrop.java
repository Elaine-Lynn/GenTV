package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One selected-wallpaper blur shared by Home's second screen and the launcher's other pages. */
final class WallpaperBackdrop {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static Bitmap cached;
    private static String cachedKey;

    private WallpaperBackdrop() { }

    static void fromHome(Context context, Bitmap frame) {
        if (frame == null || frame.isRecycled()) return;
        Bitmap copy;
        try { copy = frame.copy(Bitmap.Config.ARGB_8888, false); }
        catch (OutOfMemoryError exhausted) { return; }
        if (copy == null) return;
        synchronized (WallpaperBackdrop.class) {
            cached = copy;
            cachedKey = key(context);
        }
    }

    static void prepare(Context context) {
        Context app = context.getApplicationContext();
        String wanted = key(context);
        synchronized (WallpaperBackdrop.class) {
            if (wanted.equals(cachedKey) && cached != null) return;
        }
        WORKER.execute(() -> {
            Bitmap result = decode(app);
            if (result == null) return;
            synchronized (WallpaperBackdrop.class) {
                if (!wanted.equals(key(app)) || (wanted.equals(cachedKey) && cached != null)) {
                    result.recycle();
                    return;
                }
                cached = result;
                cachedKey = wanted;
            }
        });
    }

    static void apply(Activity activity, View target) {
        apply(activity, target, false, false);
    }

    static void applyOverlay(Activity activity, View target) {
        apply(activity, target, false, true);
    }

    static void applyImage(Activity activity, ImageView target) {
        apply(activity, target, true, false);
    }

    private static void apply(Activity activity, View target, boolean image, boolean overlay) {
        String wanted = key(activity);
        Bitmap ready = shared(wanted);
        if (ready != null) { show(activity, target, ready, image, overlay); return; }
        WORKER.execute(() -> {
            Bitmap decoded = decode(activity.getApplicationContext());
            target.post(() -> {
                if (activity.isDestroyed() || activity.isFinishing() || decoded == null) {
                    if (decoded != null) decoded.recycle();
                    return;
                }
                if (!wanted.equals(key(activity))) { decoded.recycle(); return; }
                fromHome(activity, decoded);
                show(activity, target, decoded, image, overlay);
            });
        });
    }

    private static synchronized Bitmap shared(String wanted) {
        return cached != null && wanted.equals(cachedKey) && !cached.isRecycled()
                ? cached : null;
    }

    private static Bitmap copy(String wanted) {
        Bitmap source = shared(wanted);
        if (source == null) return null;
        try { return source.copy(Bitmap.Config.ARGB_8888, false); }
        catch (OutOfMemoryError exhausted) { return null; }
    }

    static Bitmap current(Context context) { return copy(key(context)); }

    private static void show(Activity activity, View target, Bitmap bitmap, boolean image, boolean overlay) {
        if (image) {
            ((ImageView) target).setImageBitmap(bitmap);
        } else {
            BitmapDrawable picture = new BitmapDrawable(activity.getResources(), bitmap);
            picture.setGravity(Gravity.FILL);
            if (overlay) picture.setAlpha(200);
            target.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{
                    picture, new ColorDrawable(overlay ? 0x18000000 : 0x50000000)}));
        }
    }

    private static String key(Context context) {
        String selected = BundledWallpapers.selected(context);
        long revision = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getLong(Config.SETTINGS_REVISION_KEY, 0);
        File directory = Config.dir(context);
        File local = directory == null ? null : new File(directory, Config.WALLPAPER_NAME);
        String file = local == null ? "" : local.lastModified() + ":" + local.length();
        if (UserWallpaper.isImage(selected)) local = UserWallpaper.image(context, selected);
        if (UserWallpaper.isVideo(selected)) local = UserWallpaper.videoPreview(context, selected);
        if (local != null) file += ':' + local.lastModified() + ":" + local.length();
        return selected + ':' + revision + ':' + file;
    }

    private static Bitmap decode(Context context) {
        int resource = BundledWallpapers.resource(context);
        String selected = BundledWallpapers.selected(context);
        for (int index = 0; index < BundledWallpapers.MOTION_IDS.length; index++)
            if (BundledWallpapers.MOTION_IDS[index].equals(selected))
                resource = BundledWallpapers.MOTION_IMAGES[index];
        try {
            if (UserWallpaper.isImage(selected))
                return WallpaperBlur.fromFile(UserWallpaper.image(context, selected));
            if (UserWallpaper.isVideo(selected)) {
                File preview = UserWallpaper.videoPreview(context, selected);
                return UserWallpaper.previewUsable(preview) ? WallpaperBlur.fromFile(preview) : null;
            }
            Config config = Config.load(context);
            if (resource == 0 && config.wallpaper != null) {
                Bitmap local = WallpaperBlur.fromFile(config.wallpaper);
                if (local != null) return local;
            }
            try (InputStream source = context.getResources().openRawResource(
                    resource == 0 ? R.raw.wallpaper_default : resource)) {
                return WallpaperBlur.fromBytes(TvCloud.read(source, 8 * 1024 * 1024));
            }
        } catch (Exception | OutOfMemoryError ignored) { return null; }
    }
}
