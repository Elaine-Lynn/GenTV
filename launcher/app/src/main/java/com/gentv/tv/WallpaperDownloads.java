package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which online gallery wallpapers are already on the TV: catalogue item ID to the user wallpaper it was imported
 * as, keyed on the item's asset path so a renamed or replaced asset downloads again. An entry whose file was
 * removed (for example from the custom wallpaper row) counts as not downloaded.
 */
final class WallpaperDownloads {
    private static final String PREFS = "wallpaper_downloads";
    /** Every local ID ever imported from the gallery, kept even after its catalogue entry is replaced. */
    private static final String IMPORTED_PREFS = "wallpaper_downloads_imported";
    private static final String IMPORTED_KEY = "ids";

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
            SharedPreferences imported = context.getSharedPreferences(IMPORTED_PREFS, Context.MODE_PRIVATE);
            Set<String> ids = new HashSet<>(imported.getStringSet(IMPORTED_KEY, new HashSet<>()));
            ids.add(localId);
            imported.edit().putStringSet(IMPORTED_KEY, ids).commit();
        } catch (JSONException ignored) {
        }
    }

    /**
     * Local wallpaper IDs that came from the online gallery, so the custom wallpaper row shows only what the
     * user added from a file or over the LAN.
     */
    static Set<String> onlineIds(Context context) {
        Set<String> ids = new HashSet<>(context.getSharedPreferences(IMPORTED_PREFS, Context.MODE_PRIVATE)
                .getStringSet(IMPORTED_KEY, new HashSet<>()));
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            try {
                String id = new JSONObject((String) entry.getValue()).optString("id");
                if (!id.isEmpty()) ids.add(id);
            } catch (JSONException ignored) {
            }
        }
        return ids;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
