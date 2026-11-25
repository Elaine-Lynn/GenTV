package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** Optional built-in backgrounds. An empty choice keeps the existing phone/theme/remote wallpaper behavior. */
final class BundledWallpapers {
    static final String KEY = "bundled_wallpaper";
    static final String[] IDS = {"", "classic", "cobalt", "teal"};
    static final int[] IMAGES = {R.raw.wallpaper_default, R.raw.wallpaper_default,
            R.raw.wallpaper_glass_cobalt, R.raw.wallpaper_glass_teal};
    static final int[] IMAGE_PREVIEWS = {R.raw.wallpaper_default_preview,
            R.raw.wallpaper_default_preview, R.raw.wallpaper_glass_cobalt_preview,
            R.raw.wallpaper_glass_teal_preview};
    static final int[] TITLES = {R.string.wallpaper_follow_current, R.string.wallpaper_classic,
            R.string.wallpaper_cobalt, R.string.wallpaper_teal};
    static final String[] MOTION_IDS = {"motion_blue"};
    static final int[] MOTION_IMAGES = {R.raw.wallpaper_motion_blue_preview};
    static final int[] MOTION_VIDEOS = {R.raw.wallpaper_motion_blue};
    static final int[] MOTION_TITLES = {R.string.wallpaper_motion_blue};

    private BundledWallpapers() { }

    static String selected(Context context) {
        String selected = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, "");
        // An older selection keeps a blue wallpaper after the retired asset is removed.
        if ("indigo".equals(selected)) return "cobalt";
        // Older installs may have selected one of the removed bundled clips.
        if ("motion_teal".equals(selected) || "motion_indigo".equals(selected) || "motion_cat".equals(selected)
                || "motion_aquarium".equals(selected)) return MOTION_IDS[0];
        return selected;
    }

    static int resource(Context context) {
        String selected = selected(context);
        for (int i = 1; i < IDS.length; i++) if (IDS[i].equals(selected)) return IMAGES[i];
        return 0;
    }

    static int videoResource(Context context) {
        String selected = selected(context);
        for (int i = 0; i < MOTION_IDS.length; i++)
            if (MOTION_IDS[i].equals(selected)) return MOTION_VIDEOS[i];
        return 0;
    }

    static void select(Context context, String id) {
        SharedPreferences preferences = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY, id)
                .putLong(Config.SETTINGS_REVISION_KEY,
                        preferences.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}
