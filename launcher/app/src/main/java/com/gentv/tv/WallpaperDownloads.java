package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Which online gallery wallpapers are already on the TV: catalogue item ID to the user wallpaper it was imported
 * as, keyed on the item's asset path so a renamed or replaced asset downloads again. An entry whose file was
 * removed (for example from the custom wallpaper row) counts as not downloaded.
 */
final class WallpaperDownloads {
    private static final String PREFS = "wallpaper_downloads";

    private WallpaperDownloads() { }

    /** The local wallpaper ID for this catalogue item, or null when it has to be downloaded. */
    static String local(Context context, String itemId, String path) {
        String raw = prefs(context).getString(itemId, null);
        if (raw == null || path == null || path.isEmpty()) return null;
        try {
            JSONObject entry = new JSONObject(raw);
            String id = entry.optString("id");
            if (!path.equals(entry.optString("path"))) return null;
            File file = UserWallpaper.isVideo(id) ? UserWallpaper.video(context, id)
                    : UserWallpaper.isImage(id) ? UserWallpaper.image(context, id) : null;
            return file != null && file.isFile() && file.length() > 0 ? id : null;
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }

    static void remember(Context context, String itemId, String path, String localId) {
        if (itemId == null || itemId.isEmpty() || localId == null) return;
        try {
            prefs(context).edit().putString(itemId,
                    new JSONObject().put("id", localId).put("path", path).toString()).commit();
        } catch (JSONException ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
