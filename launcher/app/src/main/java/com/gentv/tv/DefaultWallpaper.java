package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;

/**
 * A fresh install starts on the online gallery's first kitten video. Home asks for it once the API is
 * configured; it downloads in the background like a gallery pick and becomes the wallpaper when done.
 * An update, or a TV where someone already chose a wallpaper, is left as it is.
 */
final class DefaultWallpaper {
    static final String ITEM_ID = "cat-loop";
    static final String FALLBACK_PATH = "wallpapers/catWallpaperLoop1080p.mp4";
    private static final String TAG = "GenTVWallpaper";
    private static final String DONE_KEY = "default_wallpaper_done";
    private static final String ACTIVE_CATALOG_KEY = "wallpaper_active_catalog_id";
    private static boolean running;

    interface Applied { void applied(); }

    private DefaultWallpaper() { }

    /** Starts the one background download when this TV qualifies. The callback runs on the main thread. */
    static void ensure(Context context, Applied applied) {
        Context app = context.getApplicationContext();
        if (running || !TvCloud.enabled()) return;
        SharedPreferences settings = app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (settings.getBoolean(DONE_KEY, false)) return;
        if (!freshInstall(app) || !untouched(app, settings)) {
            settings.edit().putBoolean(DONE_KEY, true).apply();
            return;
        }
        running = true;
        Handler main = new Handler(Looper.getMainLooper());
        TvCloud.IO.execute(() -> {
            String local = null;
            try {
                String path = catalogPath();
                local = WallpaperDownloads.local(app, ITEM_ID, path);
                if (local == null) {
                    HttpURLConnection connection = TvCloud.open(WallpaperGalleryActivity.assetUrl(path));
                    try {
                        if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
                        local = UserWallpaper.importDownload(app, connection.getInputStream(), true);
                    } finally {
                        connection.disconnect();
                    }
                    WallpaperDownloads.remember(app, ITEM_ID, path, local);
                }
            } catch (Exception error) {
                // Retried the next time Home resumes; no URL or response content is logged.
                Log.w(TAG, "Default wallpaper download failed: " + error.getClass().getSimpleName());
            }
            String downloaded = local;
            main.post(() -> {
                running = false;
                // Someone may have chosen a wallpaper while this one downloaded.
                if (downloaded == null || settings.getBoolean(DONE_KEY, false) || !untouched(app, settings)) return;
                settings.edit().putBoolean(DONE_KEY, true).putString(ACTIVE_CATALOG_KEY, ITEM_ID).apply();
                BundledWallpapers.select(app, downloaded);
                applied.applied();
            });
        });
    }

    /** The kitten's current asset path from the catalogue, or the path it has always had. */
    private static String catalogPath() {
        try {
            JSONArray groups = TvCloud.json("wallpaper?view=catalog").getJSONArray("categories");
            for (int group = 0; group < groups.length(); group++) {
                JSONArray items = groups.getJSONObject(group).optJSONArray("items");
                for (int index = 0; items != null && index < items.length(); index++) {
                    JSONObject item = items.getJSONObject(index);
                    if (ITEM_ID.equals(item.optString("id")) && "video".equals(item.optString("kind"))) {
                        return item.getString("path");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_PATH;
    }

    private static boolean freshInstall(Context app) {
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    /** Nothing chosen yet: the built-in "follow current" choice, no gallery item and no shuffle. */
    static boolean untouched(Context app, SharedPreferences settings) {
        return BundledWallpapers.selected(app).isEmpty()
                && settings.getString(ACTIVE_CATALOG_KEY, "").isEmpty()
                && !WallpaperShuffle.active(app);
    }
}
