package com.gentv.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Domain models, WMO weather code formatting, and curated global city directory. */
final class WeatherModels {
    private WeatherModels() {}

    static final class City {
        final String id;
        final String name;
        final String region;
        final String country;
        final double lat;
        final double lon;
        final String timezone;
        final boolean autoLocated;

        City(String id, String name, String region, String country,
             double lat, double lon, String timezone, boolean autoLocated) {
            this.id = id;
            this.name = name;
            this.region = region == null ? "" : region;
            this.country = country == null ? "" : country;
            this.lat = lat;
            this.lon = lon;
            this.timezone = timezone == null ? "" : timezone;
            this.autoLocated = autoLocated;
        }

        String subtitle() {
            if (!region.isEmpty() && !region.equals(name) && !country.isEmpty()) {
                return region + ", " + country;
            }
            if (!country.isEmpty()) return country;
            return region;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("region", region)
                    .put("country", country)
                    .put("lat", lat)
                    .put("lon", lon)
                    .put("timezone", timezone)
                    .put("autoLocated", autoLocated);
        }

        static City fromJson(JSONObject o) throws JSONException {
            return new City(
                    o.getString("id"),
                    o.getString("name"),
                    o.optString("region", ""),
                    o.optString("country", ""),
                    o.getDouble("lat"),
                    o.getDouble("lon"),
                    o.optString("timezone", ""),
                    o.optBoolean("autoLocated", false));
        }
    }

    static final class HourlyPoint {
        final String timeLabel;
        final double tempC;
        final int weatherCode;
        final boolean isDay;

        HourlyPoint(String timeLabel, double tempC, int weatherCode, boolean isDay) {
            this.timeLabel = timeLabel;
            this.tempC = tempC;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("t", timeLabel)
                    .put("c", tempC)
                    .put("w", weatherCode)
                    .put("d", isDay);
        }

        static HourlyPoint fromJson(JSONObject o) throws JSONException {
            return new HourlyPoint(
                    o.getString("t"),
                    o.getDouble("c"),
                    o.getInt("w"),
                    o.optBoolean("d", true));
        }
    }

    static final class DailyPoint {
        final String dateIso;
        final int weatherCode;
        final double highC;
        final double lowC;

        DailyPoint(String dateIso, int weatherCode, double highC, double lowC) {
            this.dateIso = dateIso;
            this.weatherCode = weatherCode;
            this.highC = highC;
            this.lowC = lowC;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("dt", dateIso)
                    .put("w", weatherCode)
                    .put("hi", highC)
                    .put("lo", lowC);
        }

        static DailyPoint fromJson(JSONObject o) throws JSONException {
            return new DailyPoint(
                    o.getString("dt"),
                    o.getInt("w"),
                    o.getDouble("hi"),
                    o.getDouble("lo"));
        }
    }

    static final class WeatherData {
        final String cityId;
        final long fetchedAtMs;
        final double tempC;
        final double feelsLikeC;
        final int humidity;
        final double windKmh;
        final int windDeg;
        final int weatherCode;
        final boolean isDay;
        final double highC;
        final double lowC;
        final double uvMax;
        final int aqi;
        final String sunrise;
        final String sunset;
        final String localTime;
        final String tzAbbr;
        final List<HourlyPoint> hourly;
        final List<DailyPoint> daily;

        WeatherData(String cityId, long fetchedAtMs, double tempC, double feelsLikeC,
                    int humidity, double windKmh, int windDeg, int weatherCode, boolean isDay,
                    double highC, double lowC, double uvMax, int aqi,
                    String sunrise, String sunset, String localTime, String tzAbbr,
                    List<HourlyPoint> hourly, List<DailyPoint> daily) {
            this.cityId = cityId;
            this.fetchedAtMs = fetchedAtMs;
            this.tempC = tempC;
            this.feelsLikeC = feelsLikeC;
            this.humidity = humidity;
            this.windKmh = windKmh;
            this.windDeg = windDeg;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.highC = highC;
            this.lowC = lowC;
            this.uvMax = uvMax;
            this.aqi = aqi;
            this.sunrise = sunrise == null ? "" : sunrise;
            this.sunset = sunset == null ? "" : sunset;
            this.localTime = localTime == null ? "" : localTime;
            this.tzAbbr = tzAbbr == null ? "" : tzAbbr;
            this.hourly = Collections.unmodifiableList(new ArrayList<>(hourly));
            this.daily = Collections.unmodifiableList(new ArrayList<>(daily));
        }

        JSONObject toJson() throws JSONException {
            JSONArray hArr = new JSONArray();
            for (HourlyPoint p : hourly) hArr.put(p.toJson());
            JSONArray dArr = new JSONArray();
            for (DailyPoint p : daily) dArr.put(p.toJson());
            return new JSONObject()
                    .put("cityId", cityId)
                    .put("fetchedAtMs", fetchedAtMs)
                    .put("tempC", tempC)
                    .put("feelsLikeC", feelsLikeC)
                    .put("humidity", humidity)
                    .put("windKmh", windKmh)
                    .put("windDeg", windDeg)
                    .put("weatherCode", weatherCode)
                    .put("isDay", isDay)
                    .put("highC", highC)
                    .put("lowC", lowC)
                    .put("uvMax", uvMax)
                    .put("aqi", aqi)
                    .put("sunrise", sunrise)
                    .put("sunset", sunset)
                    .put("localTime", localTime)
                    .put("tzAbbr", tzAbbr)
                    .put("hourly", hArr)
                    .put("daily", dArr);
        }

        static WeatherData fromJson(JSONObject o) throws JSONException {
            List<HourlyPoint> hourly = new ArrayList<>();
            JSONArray hArr = o.optJSONArray("hourly");
            if (hArr != null) {
                for (int i = 0; i < hArr.length(); i++) hourly.add(HourlyPoint.fromJson(hArr.getJSONObject(i)));
            }
            List<DailyPoint> daily = new ArrayList<>();
            JSONArray dArr = o.optJSONArray("daily");
            if (dArr != null) {
                for (int i = 0; i < dArr.length(); i++) daily.add(DailyPoint.fromJson(dArr.getJSONObject(i)));
            }
            return new WeatherData(
                    o.getString("cityId"),
                    o.optLong("fetchedAtMs", 0L),
                    o.getDouble("tempC"),
                    o.optDouble("feelsLikeC", o.getDouble("tempC")),
                    o.optInt("humidity", 50),
                    o.optDouble("windKmh", 0.0),
                    o.optInt("windDeg", 0),
                    o.optInt("weatherCode", 0),
                    o.optBoolean("isDay", true),
                    o.optDouble("highC", o.getDouble("tempC")),
                    o.optDouble("lowC", o.getDouble("tempC")),
                    o.optDouble("uvMax", 0.0),
                    o.optInt("aqi", -1),
                    o.optString("sunrise", ""),
                    o.optString("sunset", ""),
                    o.optString("localTime", ""),
                    o.optString("tzAbbr", ""),
                    hourly,
                    daily);
        }
    }

    static boolean isChinese(Locale locale) {
        return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
    }

    static String formatTemp(double celsius, boolean fahrenheit) {
        long rounded = Math.round(fahrenheit ? (celsius * 9.0 / 5.0 + 32.0) : celsius);
        return rounded + "°";
    }

    static String conditionLabel(int wmo, boolean isDay, boolean zh) {
        if (wmo == 0) return zh ? (isDay ? "晴朗" : "晴夜") : (isDay ? "Clear" : "Clear Night");
        if (wmo == 1) return zh ? "大部晴朗" : "Mostly Clear";
        if (wmo == 2) return zh ? "多云" : "Partly Cloudy";
        if (wmo == 3) return zh ? "阴" : "Overcast";
        if (wmo == 45 || wmo == 48) return zh ? "有雾" : "Foggy";
        if (wmo >= 51 && wmo <= 57) return zh ? "毛毛雨" : "Drizzle";
        if (wmo >= 61 && wmo <= 65) return zh ? (wmo == 65 ? "大雨" : "小到中雨") : (wmo == 65 ? "Heavy Rain" : "Rain");
        if (wmo == 66 || wmo == 67) return zh ? "冻雨" : "Freezing Rain";
        if (wmo >= 71 && wmo <= 77) return zh ? "降雪" : "Snow";
        if (wmo >= 80 && wmo <= 82) return zh ? "阵雨" : "Showers";
        if (wmo == 85 || wmo == 86) return zh ? "阵雪" : "Snow Showers";
        if (wmo >= 95) return zh ? "雷阵雨" : "Thunderstorm";
        return zh ? "多云" : "Cloudy";
    }

    static String poeticInsight(WeatherData d, boolean zh) {
        int code = d.weatherCode;
        if (code >= 95) {
            return zh ? "强对流雷雨天气，外出请注意防风避雨。" : "Thunderstorm activity nearby. Stay indoors if possible.";
        }
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82) || (code >= 51 && code <= 57)) {
            return zh ? "今日有降水，出门记得携带雨具。" : "Rain expected today. Carry an umbrella when heading out.";
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return zh ? "雪花飘落，气温偏低，注意防寒保暖。" : "Snowfall and crisp temperatures. Dress warmly today.";
        }
        if (code == 45 || code == 48) {
            return zh ? "能见度偏低，空气湿润，出行请留意路况。" : "Misty conditions with reduced visibility across the area.";
        }
        if (d.windKmh >= 28) {
            return zh ? "风力较大，体感偏凉，建议添衣防风。" : "Breezy conditions across the city with gusty winds.";
        }
        if (code <= 1) {
            return zh ? "天朗气清，微风柔和，适合惬意出行。" : "Clear skies and a gentle breeze through tonight.";
        }
        return zh ? "云层舒展，微风轻拂，全天气温舒适平稳。" : "Mild clouds and a calm breeze expected through the evening.";
    }

    static int iconRes(int wmo, boolean isDay) {
        if (wmo >= 95) return R.drawable.ic_w_storm;
        if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) return R.drawable.ic_w_snow;
        if ((wmo >= 51 && wmo <= 67) || (wmo >= 80 && wmo <= 82)) return R.drawable.ic_w_rain;
        if (wmo == 45 || wmo == 48) return R.drawable.ic_w_fog;
        if (wmo == 0) return isDay ? R.drawable.ic_w_sun : R.drawable.ic_w_moon;
        if (wmo == 1 || wmo == 2) return isDay ? R.drawable.ic_w_partly : R.drawable.ic_w_moon;
        return R.drawable.ic_w_cloud;
    }

    static String aqiLabel(int aqi, boolean zh) {
        if (aqi < 0) return zh ? "—" : "—";
        if (aqi <= 50) return zh ? "优" : "Good";
        if (aqi <= 100) return zh ? "良" : "Moderate";
        if (aqi <= 150) return zh ? "轻度污染" : "Sensitive";
        if (aqi <= 200) return zh ? "中度污染" : "Unhealthy";
        return zh ? "重度污染" : "Poor";
    }

    static int aqiDotColor(int aqi) {
        if (aqi < 0) return 0x66FFFFFF;
        if (aqi <= 50) return 0xFF5EE39B;
        if (aqi <= 100) return 0xFFF4D06F;
        if (aqi <= 150) return 0xFFFF9F5A;
        return 0xFFFF6B7A;
    }

    static String uvLabel(double uv, boolean zh) {
        if (uv < 3) return zh ? "弱" : "Low";
        if (uv < 6) return zh ? "中等" : "Moderate";
        if (uv < 8) return zh ? "强" : "High";
        return zh ? "极强" : "Very High";
    }

    static String windDirection(int degrees, boolean zh) {
        int idx = Math.round(((degrees % 360) + 360) % 360 / 45f) % 8;
        if (zh) {
            String[] dirs = {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};
            return dirs[idx];
        }
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[idx];
    }

    /** Curated offline global city directory for effortless 1-click TV remote selection. */
    static List<City> globalPresets(boolean zh) {
        List<City> list = new ArrayList<>();
        list.add(new City("sf", zh ? "旧金山" : "San Francisco", zh ? "加利福尼亚" : "California", zh ? "美国" : "United States", 37.7749, -122.4194, "America/Los_Angeles", false));
        list.add(new City("tokyo", zh ? "东京" : "Tokyo", zh ? "东京都" : "Tokyo", zh ? "日本" : "Japan", 35.6762, 139.6503, "Asia/Tokyo", false));
        list.add(new City("london", zh ? "伦敦" : "London", zh ? "英格兰" : "England", zh ? "英国" : "United Kingdom", 51.5072, -0.1276, "Europe/London", false));
        list.add(new City("nyc", zh ? "纽约" : "New York", zh ? "纽约州" : "New York", zh ? "美国" : "United States", 40.7128, -74.0060, "America/New_York", false));
        list.add(new City("zurich", zh ? "苏黎世" : "Zurich", zh ? "苏黎世州" : "Zurich", zh ? "瑞士" : "Switzerland", 47.3769, 8.5417, "Europe/Zurich", false));
        list.add(new City("hangzhou", zh ? "杭州" : "Hangzhou", zh ? "浙江" : "Zhejiang", zh ? "中国" : "China", 30.2741, 120.1551, "Asia/Shanghai", false));
        list.add(new City("beijing", zh ? "北京" : "Beijing", zh ? "北京市" : "Beijing", zh ? "中国" : "China", 39.9042, 116.4074, "Asia/Shanghai", false));
        list.add(new City("shanghai", zh ? "上海" : "Shanghai", zh ? "上海市" : "Shanghai", zh ? "中国" : "China", 31.2304, 121.4737, "Asia/Shanghai", false));
        list.add(new City("shenzhen", zh ? "深圳" : "Shenzhen", zh ? "广东" : "Guangdong", zh ? "中国" : "China", 22.5431, 114.0579, "Asia/Shanghai", false));
        list.add(new City("hongkong", zh ? "香港" : "Hong Kong", zh ? "香港" : "Hong Kong", zh ? "中国" : "China", 22.3193, 114.1694, "Asia/Hong_Kong", false));
        list.add(new City("taipei", zh ? "台北" : "Taipei", zh ? "台湾" : "Taiwan", zh ? "中国" : "China", 25.0330, 121.5654, "Asia/Taipei", false));
        list.add(new City("seoul", zh ? "首尔" : "Seoul", zh ? "首尔" : "Seoul", zh ? "韩国" : "South Korea", 37.5665, 126.9780, "Asia/Seoul", false));
        list.add(new City("singapore", zh ? "新加坡" : "Singapore", "", zh ? "新加坡" : "Singapore", 1.3521, 103.8198, "Asia/Singapore", false));
        list.add(new City("sydney", zh ? "悉尼" : "Sydney", zh ? "新南威尔士" : "NSW", zh ? "澳大利亚" : "Australia", -33.8688, 151.2093, "Australia/Sydney", false));
        list.add(new City("paris", zh ? "巴黎" : "Paris", zh ? "法兰西岛" : "Ile-de-France", zh ? "法国" : "France", 48.8566, 2.3522, "Europe/Paris", false));
        list.add(new City("berlin", zh ? "柏林" : "Berlin", zh ? "柏林" : "Berlin", zh ? "德国" : "Germany", 52.5200, 13.4050, "Europe/Berlin", false));
        list.add(new City("la", zh ? "洛杉矶" : "Los Angeles", zh ? "加利福尼亚" : "California", zh ? "美国" : "United States", 34.0522, -118.2437, "America/Los_Angeles", false));
        list.add(new City("toronto", zh ? "多伦多" : "Toronto", zh ? "安大略" : "Ontario", zh ? "加拿大" : "Canada", 43.6532, -79.3832, "America/Toronto", false));
        list.add(new City("dubai", zh ? "迪拜" : "Dubai", zh ? "迪拜" : "Dubai", zh ? "阿联酋" : "UAE", 25.2048, 55.2708, "Asia/Dubai", false));
        list.add(new City("chengdu", zh ? "成都" : "Chengdu", zh ? "四川" : "Sichuan", zh ? "中国" : "China", 30.5728, 104.0668, "Asia/Shanghai", false));
        return list;
    }

    static List<City> defaultCities(boolean zh) {
        List<City> presets = globalPresets(zh);
        List<City> defaults = new ArrayList<>();
        if (zh) {
            defaults.add(presets.get(5)); // Hangzhou
            defaults.add(presets.get(0)); // San Francisco
            defaults.add(presets.get(1)); // Tokyo
            defaults.add(presets.get(2)); // London
        } else {
            defaults.add(presets.get(0)); // San Francisco
            defaults.add(presets.get(1)); // Tokyo
            defaults.add(presets.get(2)); // London
            defaults.add(presets.get(3)); // New York
        }
        return defaults;
    }
}
