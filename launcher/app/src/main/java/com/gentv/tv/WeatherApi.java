package com.gentv.tv;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;

/** Bounded, HTTPS-only Open-Meteo requests. No device location or API key is needed. */
final class WeatherApi {
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast?";

    static final class City {
        final String label;
        final double latitude, longitude;
        City(String label, double latitude, double longitude) {
            this.label = label; this.latitude = latitude; this.longitude = longitude;
        }
    }

    static final class Current {
        final int temperature, code, maximum, minimum;
        Current(int temperature, int code) { this(temperature, code, temperature, temperature); }
        Current(int temperature, int code, int maximum, int minimum) {
            this.temperature = temperature; this.code = code;
            this.maximum = maximum; this.minimum = minimum;
        }
    }

    static Current current(double latitude, double longitude) throws Exception {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new IOException("Invalid coordinates");
        String url = FORECAST + String.format(Locale.US,
                "latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
                latitude, longitude);
        JSONObject response = get(url);
        JSONObject current = response.getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        int code = current.getInt("weather_code");
        JSONObject daily = response.getJSONObject("daily");
        double maximum = daily.getJSONArray("temperature_2m_max").getDouble(0);
        double minimum = daily.getJSONArray("temperature_2m_min").getDouble(0);
        if (!Double.isFinite(temperature) || temperature < -100 || temperature > 70 || code < 0 || code > 99)
            throw new IOException("Invalid weather response");
        if (!Double.isFinite(maximum) || !Double.isFinite(minimum)
                || maximum < -100 || maximum > 70 || minimum < -100 || minimum > 70 || maximum < minimum)
            throw new IOException("Invalid daily weather response");
        return new Current((int) Math.round(temperature), code,
                (int) Math.round(maximum), (int) Math.round(minimum));
    }

    private static JSONObject get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(12_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "GenTV/" + BuildConfig.VERSION_NAME);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Weather HTTP " + connection.getResponseCode());
            return new JSONObject(new String(TvCloud.read(connection.getInputStream(), 64 * 1024), Charset.forName("UTF-8")));
        } finally { connection.disconnect(); }
    }

    static int condition(int code) {
        if (code == 0 || code == 1) return R.string.weather_clear;
        if (code == 2 || code == 3) return R.string.weather_cloudy;
        if (code == 45 || code == 48) return R.string.weather_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.string.weather_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.string.weather_snow;
        if (code >= 95) return R.string.weather_storm;
        return R.string.weather_cloudy;
    }

    static String symbol(int code) {
        if (code <= 1) return "☼";
        if (code <= 3) return "☁";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        if (code >= 51 || code == 45 || code == 48) return "☂";
        return "☁";
    }
}
