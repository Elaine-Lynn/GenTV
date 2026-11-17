package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

enum PondOption {
    COUNT("pond_count", R.string.pond_count, R.string.pond_count_value, 20, 10, 20, 30),
    SPEED("pond_speed", R.string.pond_speed, R.string.pond_speed_value, 100, 75, 100, 125),
    FPS("pond_fps", R.string.pond_fps, R.string.pond_fps_value, 30, 15, 20, 30),
    RESOLUTION("pond_resolution", R.string.pond_resolution, R.string.pond_resolution_value, 1080, 720, 1080);

    private final String key;
    final int title;
    private final int valueLabel;
    private final int defaultValue;
    final int[] values;

    PondOption(String key, int title, int valueLabel, int defaultValue, int... values) {
        this.key = key;
        this.title = title;
        this.valueLabel = valueLabel;
        this.defaultValue = defaultValue;
        this.values = values;
    }

    int read(SharedPreferences preferences) {
        int saved = preferences.getInt(key, defaultValue);
        for (int value : values) if (saved == value) return value;
        return defaultValue;
    }

    void write(SharedPreferences preferences, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    String label(Context context, int value) {
        return context.getString(valueLabel, value);
    }
}
