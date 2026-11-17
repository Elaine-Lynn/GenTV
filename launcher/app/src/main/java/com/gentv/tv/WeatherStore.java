package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** The manually selected city and the last successful current-weather reading. */
final class WeatherStore {
    private static final String FILE = "weather";
    private static final String CITY = "city";
    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    private static final String TEMPERATURE = "temperature";
    private static final String CODE = "code";
    private static final String MAXIMUM = "maximum";
    private static final String MINIMUM = "minimum";
    private static final String UPDATED = "updated";
    private static final String ATTEMPTED = "attempted";

    static SharedPreferences prefs(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static String city(Context context) { return prefs(context).getString(CITY, ""); }
    static double latitude(Context context) { return Double.longBitsToDouble(prefs(context).getLong(LATITUDE, 0)); }
    static double longitude(Context context) { return Double.longBitsToDouble(prefs(context).getLong(LONGITUDE, 0)); }

    static void select(Context context, WeatherApi.City city) {
        prefs(context).edit().putString(CITY, city.label)
                .putLong(LATITUDE, Double.doubleToLongBits(city.latitude))
                .putLong(LONGITUDE, Double.doubleToLongBits(city.longitude))
                .remove(UPDATED).remove(ATTEMPTED).apply();
        changed(context);
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
        changed(context);
    }

    static void save(Context context, WeatherApi.Current weather, long now) {
        prefs(context).edit().putInt(TEMPERATURE, weather.temperature).putInt(CODE, weather.code)
                .putInt(MAXIMUM, weather.maximum).putInt(MINIMUM, weather.minimum)
                .putLong(UPDATED, now).putLong(ATTEMPTED, now).apply();
    }

    static void attempted(Context context, long now) { prefs(context).edit().putLong(ATTEMPTED, now).apply(); }
    static long updated(Context context) { return prefs(context).getLong(UPDATED, 0); }
    static long lastAttempt(Context context) { return prefs(context).getLong(ATTEMPTED, 0); }
    static WeatherApi.Current cached(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getLong(UPDATED, 0) > 0
                ? new WeatherApi.Current(prefs.getInt(TEMPERATURE, 0), prefs.getInt(CODE, 0),
                    prefs.getInt(MAXIMUM, prefs.getInt(TEMPERATURE, 0)),
                    prefs.getInt(MINIMUM, prefs.getInt(TEMPERATURE, 0))) : null;
    }

    private static void changed(Context context) {
        SharedPreferences settings = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        settings.edit().putLong(Config.SETTINGS_REVISION_KEY,
                settings.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}
