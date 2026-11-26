package com.gentv.tv;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class AutomaticWeatherCityTest {
    private ChinaRegions.Region city(String name) {
        return new ChinaRegions.Region(name, 121.47, 31.23, Collections.emptyList());
    }
    @Test public void matchesMunicipalityAndUsesBundledCoordinates() throws Exception {
        ChinaRegions.Region shanghai = new ChinaRegions.Region("上海市", 0, 0, Arrays.asList(city("上海市")));
        WeatherApi.City result = ChinaRegions.matchCity(Arrays.asList(shanghai), "上海市", "上海市");
        assertEquals("上海市", result.label);
        assertEquals(31.23, result.latitude, 0.0001);
        assertEquals(121.47, result.longitude, 0.0001);
    }
    @Test(expected=java.io.IOException.class) public void doesNotGuessCityWhenProvinceDiffers() throws Exception {
        ChinaRegions.matchCity(Arrays.asList(new ChinaRegions.Region("上海市", 0, 0, Arrays.asList(city("上海市")))),
                "江苏省", "上海市");
    }
    @Test(expected=java.io.IOException.class) public void rejectsUnknownCity() throws Exception {
        ChinaRegions.matchCity(Arrays.asList(new ChinaRegions.Region("上海市", 0, 0, Arrays.asList(city("上海市")))),
                "上海市", "未知城市");
    }
}
