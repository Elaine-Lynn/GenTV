package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** Optional built-in backgrounds. An empty choice keeps the existing phone/theme/remote wallpaper behavior. */
final class BundledWallpapers {
    static final String KEY = "bundled_wallpaper";
    static final String[] IDS = {"", "classic", "cobalt", "teal", "indigo"};
    static final int[] IMAGES = {R.raw.wallpaper_default, R.raw.wallpaper_default,
            R.raw.wallpaper_glass_cobalt, R.raw.wallpaper_glass_teal, R.raw.wallpaper_glass_indigo};
    static final int[] IMAGE_PREVIEWS = {R.raw.wallpaper_default_preview,
            R.raw.wallpaper_default_preview, R.raw.wallpaper_glass_cobalt_preview,
            R.raw.wallpaper_glass_teal_preview, R.raw.wallpaper_glass_indigo_preview};
    static final int[] TITLES = {R.string.wallpaper_follow_current, R.string.wallpaper_classic,
            R.string.wallpaper_cobalt, R.string.wallpaper_teal, R.string.wallpaper_indigo};
    static final String[] MOTION_IDS = {"motion_blue", "motion_teal", "motion_indigo",
            "motion_cat", "motion_aquarium"};
    static final int[] MOTION_IMAGES = {R.raw.wallpaper_motion_blue_preview,
            R.raw.wallpaper_motion_teal_preview, R.raw.wallpaper_motion_indigo_preview,
            R.raw.wallpaper_motion_cat_preview, R.raw.wallpaper_motion_aquarium_preview};
    static final int[] MOTION_VIDEOS = {R.raw.wallpaper_motion_blue,
            R.raw.wallpaper_motion_teal, R.raw.wallpaper_motion_indigo,
            R.raw.wallpaper_motion_cat, R.raw.wallpaper_motion_aquarium};
    static final int[] MOTION_TITLES = {R.string.wallpaper_motion_blue,
            R.string.wallpaper_motion_teal, R.string.wallpaper_motion_indigo,
            R.string.wallpaper_motion_cat, R.string.wallpaper_motion_aquarium};

    private BundledWallpapers() { }

    static String selected(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, "");
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
