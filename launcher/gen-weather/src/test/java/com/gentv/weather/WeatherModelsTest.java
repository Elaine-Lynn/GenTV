package com.gentv.weather;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WeatherModelsTest {
    @Test
    public void cityJsonRoundTrip() throws Exception {
        WeatherModels.City orig = new WeatherModels.City(
                "sf", "San Francisco", "California", "United States",
                37.7749, -122.4194, "America/Los_Angeles", true);
        WeatherModels.City restored = WeatherModels.City.fromJson(orig.toJson());
        assertEquals("sf", restored.id);
        assertEquals("San Francisco", restored.name);
        assertEquals("California, United States", restored.subtitle());
        assertEquals(37.7749, restored.lat, 0.0001);
        assertEquals(-122.4194, restored.lon, 0.0001);
        assertTrue(restored.autoLocated);
    }

    @Test
    public void weatherDataJsonRoundTrip() throws Exception {
        List<WeatherModels.HourlyPoint> hourly = Arrays.asList(
                new WeatherModels.HourlyPoint("NOW", 18.2, 2, true),
                new WeatherModels.HourlyPoint("16:00", 19.6, 0, true));
        List<WeatherModels.DailyPoint> daily = Arrays.asList(
                new WeatherModels.DailyPoint("2026-11-24", 2, 22.0, 14.0),
                new WeatherModels.DailyPoint("2026-11-25", 61, 17.5, 11.2));
        WeatherModels.WeatherData orig = new WeatherModels.WeatherData(
                "sf", 1700000000000L, 18.2, 17.8, 62, 12.4, 290, 2, true,
                22.0, 14.0, 3.2, 24, "06:42", "18:42", "14:20", "PST", hourly, daily);

        WeatherModels.WeatherData restored = WeatherModels.WeatherData.fromJson(orig.toJson());
        assertEquals("sf", restored.cityId);
        assertEquals(18.2, restored.tempC, 0.01);
        assertEquals(24, restored.aqi);
        assertEquals("18:42", restored.sunset);
        assertEquals(2, restored.hourly.size());
        assertEquals("16:00", restored.hourly.get(1).timeLabel);
        assertEquals(2, restored.daily.size());
        assertEquals(61, restored.daily.get(1).weatherCode);
    }

    @Test
    public void temperatureAndUnitFormatting() {
        assertEquals("18°", WeatherModels.formatTemp(18.2, false));
        assertEquals("65°", WeatherModels.formatTemp(18.2, true));
        assertEquals("32°", WeatherModels.formatTemp(0.0, true));
        assertEquals("0°", WeatherModels.formatTemp(-0.2, false));
    }

    @Test
    public void wmoAndMetricHelpers() {
        assertTrue(WeatherModels.isChinese(Locale.SIMPLIFIED_CHINESE));
        assertFalse(WeatherModels.isChinese(Locale.US));
        assertEquals("Partly Cloudy", WeatherModels.conditionLabel(2, true, false));
        assertEquals("多云", WeatherModels.conditionLabel(2, true, true));
        assertEquals("优", WeatherModels.aqiLabel(24, true));
        assertEquals("Good", WeatherModels.aqiLabel(24, false));
        assertEquals("NW", WeatherModels.windDirection(315, false));
        assertEquals("西北风", WeatherModels.windDirection(315, true));
        assertEquals("Low", WeatherModels.uvLabel(2.1, false));
        assertEquals("弱", WeatherModels.uvLabel(2.1, true));
        assertTrue(WeatherModels.globalPresets(true).size() >= 20);
        assertEquals(4, WeatherModels.defaultCities(false).size());
    }
}
