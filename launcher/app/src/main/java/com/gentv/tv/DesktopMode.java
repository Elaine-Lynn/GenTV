package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

enum DesktopMode {
    SIMPLE("simple"),
    POND("pond");

    private static final String KEY = "desktop_mode";
    private final String value;

    DesktopMode(String value) {
        this.value = value;
    }

    static DesktopMode current(Context context) {
        return POND.value.equals(settings(context).getString(KEY, SIMPLE.value)) ? POND : SIMPLE;
    }

    static int settingsTitle(Context context) {
        return current(context) == POND ? R.string.pond_settings : R.string.launcher_settings;
    }

    void select(Context context) {
        SharedPreferences preferences = settings(context);
        preferences.edit()
                .putString(KEY, value)
                .putLong(Config.SETTINGS_REVISION_KEY,
                        preferences.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
    }

    private static SharedPreferences settings(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
