package com.gentv.weather;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local SharedPreferences persistence for saved global cities, active city, units, and cached weather. */
final class WeatherStore {
    static final String PREFS = "gen_weather_store";
    static final String KEY_CITIES = "cities";
    static final String KEY_ACTIVE_ID = "active_city_id";
    static final String KEY_FAHRENHEIT = "use_fahrenheit";
    static final String KEY_AUTO_LOCATED_ONCE = "auto_located_once";
    static final String KEY_REVISION = "snapshot_revision";

    private WeatherStore() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<WeatherModels.City> cities(Context context) {
        String raw = prefs(context).getString(KEY_CITIES, null);
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                List<WeatherModels.City> out = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    out.add(WeatherModels.City.fromJson(array.getJSONObject(i)));
                }
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                // Fall back to curated defaults.
            }
        }
        boolean zh = WeatherModels.isChinese(context.getResources().getConfiguration().getLocales().get(0));
        List<WeatherModels.City> defaults = WeatherModels.defaultCities(zh);
        saveCities(context, defaults);
        return defaults;
    }

    static void saveCities(Context context, List<WeatherModels.City> cities) {
        try {
            JSONArray array = new JSONArray();
            for (WeatherModels.City city : cities) array.put(city.toJson());
            prefs(context).edit()
                    .putString(KEY_CITIES, array.toString())
                    .putLong(KEY_REVISION, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    static WeatherModels.City activeCity(Context context) {
        List<WeatherModels.City> all = cities(context);
        String activeId = prefs(context).getString(KEY_ACTIVE_ID, "");
        for (WeatherModels.City city : all) {
            if (city.id.equals(activeId)) return city;
        }
        return all.get(0);
    }

    static void setActiveCity(Context context, String cityId) {
        prefs(context).edit()
                .putString(KEY_ACTIVE_ID, cityId)
                .putLong(KEY_REVISION, System.currentTimeMillis())
                .apply();
    }

    static void addOrPromoteCity(Context context, WeatherModels.City city, boolean prepend) {
        List<WeatherModels.City> list = new ArrayList<>(cities(context));
        for (int i = list.size() - 1; i >= 0; i--) {
            WeatherModels.City existing = list.get(i);
            boolean sameCoords = Math.abs(existing.lat - city.lat) < 0.08
                    && Math.abs(existing.lon - city.lon) < 0.08;
            if (existing.id.equals(city.id) || sameCoords) {
                list.remove(i);
            }
        }
        if (prepend) list.add(0, city);
        else list.add(city);
        while (list.size() > 8) list.remove(list.size() - 1);
        saveCities(context, list);
        setActiveCity(context, city.id);
    }

    static void removeCity(Context context, String cityId) {
        List<WeatherModels.City> list = new ArrayList<>(cities(context));
        if (list.size() <= 1) return;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(cityId)) list.remove(i);
        }
        if (list.isEmpty()) return;
        saveCities(context, list);
        if (cityId.equals(prefs(context).getString(KEY_ACTIVE_ID, ""))) {
            setActiveCity(context, list.get(0).id);
        }
    }

    static boolean useFahrenheit(Context context) {
        if (!prefs(context).contains(KEY_FAHRENHEIT)) {
            String country = Locale.getDefault().getCountry();
            return "US".equalsIgnoreCase(country);
        }
        return prefs(context).getBoolean(KEY_FAHRENHEIT, false);
    }

    static void setUseFahrenheit(Context context, boolean fahrenheit) {
        prefs(context).edit()
                .putBoolean(KEY_FAHRENHEIT, fahrenheit)
                .putLong(KEY_REVISION, System.currentTimeMillis())
                .apply();
    }

    static boolean hasAutoLocatedOnce(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_LOCATED_ONCE, false);
    }

    static void markAutoLocated(Context context) {
        prefs(context).edit().putBoolean(KEY_AUTO_LOCATED_ONCE, true).apply();
    }

    static WeatherModels.WeatherData cachedWeather(Context context, String cityId) {
        String raw = prefs(context).getString("cache_" + cityId, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return WeatherModels.WeatherData.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    static void saveWeather(Context context, WeatherModels.WeatherData data) {
        try {
            prefs(context).edit()
                    .putString("cache_" + data.cityId, data.toJson().toString())
                    .putLong(KEY_REVISION, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }
}
