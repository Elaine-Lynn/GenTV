package com.gentv.weather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure-HTTPS, zero-API-key global weather, air quality, geocoding, and IP geolocation client:
 * - Weather & AQI: Open-Meteo Forecast API + Open-Meteo Air Quality API
 * - City Search:   Open-Meteo Geocoding API (GeoNames, multilingual)
 * - IP Location:   IP.SB GeoIP -> IPinfo.io dual HTTPS fallback
 */
final class WeatherApiClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private WeatherApiClient() {}

    static WeatherModels.WeatherData fetchWeather(WeatherModels.City city) throws Exception {
        String forecastUrl = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,is_day"
                        + "&hourly=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max"
                        + "&timezone=auto&forecast_days=7",
                city.lat, city.lon);

        JSONObject root = new JSONObject(httpGet(forecastUrl));
        JSONObject current = root.getJSONObject("current");
        double tempC = current.getDouble("temperature_2m");
        double feelsC = current.optDouble("apparent_temperature", tempC);
        int humidity = (int) Math.round(current.optDouble("relative_humidity_2m", 50));
        int wmo = current.optInt("weather_code", 0);
        double windKmh = current.optDouble("wind_speed_10m", 0);
        int windDeg = (int) Math.round(current.optDouble("wind_direction_10m", 0));
        boolean isDay = current.optInt("is_day", 1) == 1;
        String currentTime = current.optString("time", "");
        String localTime = extractClock(currentTime);
        String tzAbbr = root.optString("timezone_abbreviation", "");

        // Parse 5 spaced hourly points starting from current hour (+0, +2, +4, +6, +8)
        List<WeatherModels.HourlyPoint> hourly = new ArrayList<>();
        JSONObject hourlyObj = root.optJSONObject("hourly");
        if (hourlyObj != null) {
            JSONArray times = hourlyObj.optJSONArray("time");
            JSONArray temps = hourlyObj.optJSONArray("temperature_2m");
            JSONArray codes = hourlyObj.optJSONArray("weather_code");
            JSONArray days = hourlyObj.optJSONArray("is_day");
            if (times != null && temps != null && codes != null) {
                int startIdx = findCurrentHourIndex(times, currentTime);
                int[] offsets = {0, 2, 4, 6, 8};
                for (int k = 0; k < offsets.length; k++) {
                    int idx = startIdx + offsets[k];
                    if (idx >= times.length()) break;
                    String label = k == 0 ? "NOW" : extractClock(times.optString(idx, ""));
                    boolean ptDay = days == null || days.optInt(idx, 1) == 1;
                    hourly.add(new WeatherModels.HourlyPoint(
                            label,
                            temps.optDouble(idx, tempC),
                            codes.optInt(idx, wmo),
                            ptDay));
                }
            }
        }

        // Parse 7-day forecast + today's high/low/sunrise/sunset/UV
        List<WeatherModels.DailyPoint> daily = new ArrayList<>();
        double highC = tempC;
        double lowC = tempC;
        double uvMax = 0;
        String sunrise = "";
        String sunset = "";
        JSONObject dailyObj = root.optJSONObject("daily");
        if (dailyObj != null) {
            JSONArray dates = dailyObj.optJSONArray("time");
            JSONArray codes = dailyObj.optJSONArray("weather_code");
            JSONArray maxs = dailyObj.optJSONArray("temperature_2m_max");
            JSONArray mins = dailyObj.optJSONArray("temperature_2m_min");
            JSONArray sunrises = dailyObj.optJSONArray("sunrise");
            JSONArray sunsets = dailyObj.optJSONArray("sunset");
            JSONArray uvs = dailyObj.optJSONArray("uv_index_max");
            if (dates != null && codes != null && maxs != null && mins != null && dates.length() > 0) {
                highC = maxs.optDouble(0, tempC);
                lowC = mins.optDouble(0, tempC);
                if (sunrises != null && sunrises.length() > 0) sunrise = extractClock(sunrises.optString(0, ""));
                if (sunsets != null && sunsets.length() > 0) sunset = extractClock(sunsets.optString(0, ""));
                if (uvs != null && uvs.length() > 0) uvMax = uvs.optDouble(0, 0);
                for (int i = 0; i < Math.min(7, dates.length()); i++) {
                    daily.add(new WeatherModels.DailyPoint(
                            dates.optString(i, ""),
                            codes.optInt(i, 0),
                            maxs.optDouble(i, tempC),
                            mins.optDouble(i, tempC)));
                }
            }
        }

        int aqi = fetchAqiBestEffort(city.lat, city.lon);

        return new WeatherModels.WeatherData(
                city.id, System.currentTimeMillis(),
                tempC, feelsC, humidity, windKmh, windDeg, wmo, isDay,
                highC, lowC, uvMax, aqi, sunrise, sunset, localTime, tzAbbr,
                hourly, daily);
    }

    private static int fetchAqiBestEffort(double lat, double lon) {
        try {
            String aqiUrl = String.format(Locale.US,
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=us_aqi&timezone=auto",
                    lat, lon);
            JSONObject obj = new JSONObject(httpGet(aqiUrl));
            JSONObject current = obj.optJSONObject("current");
            if (current != null && !current.isNull("us_aqi")) {
                return (int) Math.round(current.getDouble("us_aqi"));
            }
        } catch (Exception ignored) {
            // AQI is optional enrichment; never fail the primary weather forecast.
        }
        return -1;
    }

    /** Multilingual global city search powered by Open-Meteo Geocoding API (GeoNames). */
    static List<WeatherModels.City> searchCities(String query, String lang) throws Exception {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) return new ArrayList<>();
        String encoded = URLEncoder.encode(trimmed, "UTF-8");
        String language = (lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh")) ? "zh" : "en";
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded
                + "&count=10&language=" + language + "&format=json";
        JSONObject root = new JSONObject(httpGet(url));
        JSONArray results = root.optJSONArray("results");
        List<WeatherModels.City> out = new ArrayList<>();
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            String id = "geo_" + item.optLong("id", i);
            out.add(new WeatherModels.City(
                    id,
                    item.optString("name", trimmed),
                    item.optString("admin1", ""),
                    item.optString("country", ""),
                    item.getDouble("latitude"),
                    item.getDouble("longitude"),
                    item.optString("timezone", ""),
                    false));
        }
        return out;
    }

    /** Free HTTPS IP geolocation with dual-provider fallback (IP.SB -> IPinfo.io). */
    static WeatherModels.City locateByIp(String lang) {
        WeatherModels.City city = locateViaIpSb();
        if (city == null) city = locateViaIpInfo();
        if (city == null) return null;

        // If user UI is Chinese and IP service returned an English city name, localize cleanly via Geocoding.
        if (lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh") && isAscii(city.name)) {
            try {
                List<WeatherModels.City> matches = searchCities(city.name, "zh");
                for (WeatherModels.City candidate : matches) {
                    if (Math.abs(candidate.lat - city.lat) < 1.2 && Math.abs(candidate.lon - city.lon) < 1.2) {
                        return new WeatherModels.City(
                                "ip_" + candidate.id,
                                candidate.name,
                                candidate.region,
                                candidate.country,
                                city.lat,
                                city.lon,
                                city.timezone.isEmpty() ? candidate.timezone : city.timezone,
                                true);
                    }
                }
            } catch (Exception ignored) {
                // Keep original IP city name.
            }
        }
        return city;
    }

    private static WeatherModels.City locateViaIpSb() {
        try {
            JSONObject o = new JSONObject(httpGet("https://api.ip.sb/geoip"));
            if (!o.has("latitude") || !o.has("longitude")) return null;
            double lat = o.getDouble("latitude");
            double lon = o.getDouble("longitude");
            String name = o.optString("city", "");
            if (name.isEmpty()) name = o.optString("region", "");
            if (name.isEmpty()) name = o.optString("country", "");
            if (name.isEmpty()) return null;
            return new WeatherModels.City(
                    "ip_auto",
                    name,
                    o.optString("region", ""),
                    o.optString("country", ""),
                    lat,
                    lon,
                    o.optString("timezone", ""),
                    true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static WeatherModels.City locateViaIpInfo() {
        try {
            JSONObject o = new JSONObject(httpGet("https://ipinfo.io/json"));
            String loc = o.optString("loc", "");
            String[] parts = loc.split(",");
            if (parts.length != 2) return null;
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            String name = o.optString("city", "");
            if (name.isEmpty()) name = o.optString("region", "");
            if (name.isEmpty()) return null;
            return new WeatherModels.City(
                    "ip_auto",
                    name,
                    o.optString("region", ""),
                    o.optString("country", ""),
                    lat,
                    lon,
                    o.optString("timezone", ""),
                    true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int findCurrentHourIndex(JSONArray times, String currentIso) {
        if (currentIso == null || currentIso.length() < 13) return 0;
        String hourPrefix = currentIso.substring(0, 13);
        for (int i = 0; i < times.length(); i++) {
            String t = times.optString(i, "");
            if (t.startsWith(hourPrefix)) return i;
        }
        return 0;
    }

    private static String extractClock(String iso) {
        if (iso == null) return "";
        int t = iso.indexOf('T');
        if (t >= 0 && iso.length() >= t + 6) {
            return iso.substring(t + 1, t + 6);
        }
        return "";
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) return false;
        }
        return true;
    }

    private static String httpGet(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "GenWeatherTV/1.0");
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                int total = 0;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                    out.write(buf, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conn.disconnect();
        }
    }
}
