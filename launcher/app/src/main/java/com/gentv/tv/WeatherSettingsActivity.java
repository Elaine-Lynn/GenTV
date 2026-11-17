package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Three linked province, city and district wheels for the TV remote. */
public final class WeatherSettingsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WeatherWheelView provinces;
    private WeatherWheelView cities;
    private WeatherWheelView districts;
    private TextView status;
    private List<ChinaRegions.Region> regions;
    private ChinaRegions.Region selectedProvince;
    private ChinaRegions.Region selectedCity;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather_settings);
        WallpaperBackdrop.apply(this, ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        provinces = findViewById(R.id.weatherProvinces);
        cities = findViewById(R.id.weatherCities);
        districts = findViewById(R.id.weatherDistricts);
        status = findViewById(R.id.weatherStatus);
        provinces.setTitle(getString(R.string.weather_province));
        cities.setTitle(getString(R.string.weather_city));
        districts.setTitle(getString(R.string.weather_district));
        provinces.setOnSelectionChanged(this::provinceChanged);
        cities.setOnSelectionChanged(this::cityChanged);
        provinces.setOnClickListener(v -> cities.requestFocus());
        cities.setOnClickListener(v -> districts.requestFocus());
        districts.setOnClickListener(v -> saveDistrict());

        String saved = WeatherStore.city(this);
        status.setText(saved.isEmpty() ? getString(R.string.weather_no_city)
                : getString(R.string.weather_selected_city, saved));
        worker.execute(() -> {
            List<ChinaRegions.Region> loaded = null;
            try { loaded = ChinaRegions.load(this); }
            catch (Exception ignored) { }
            List<ChinaRegions.Region> result = loaded;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    status.setText(R.string.weather_regions_unavailable);
                    return;
                }
                regions = result;
                int[] restored = findSaved(saved);
                provinces.setItems(names(regions), restored[0]);
                provinceChanged(restored[1], restored[2]);
                provinces.requestFocus();
            });
        });
    }

    private static List<String> names(List<ChinaRegions.Region> entries) {
        List<String> names = new ArrayList<>(entries.size());
        for (ChinaRegions.Region entry : entries) names.add(entry.name);
        return names;
    }

    private int[] findSaved(String saved) {
        if (!saved.isEmpty()) {
            for (int p = 0; p < regions.size(); p++) {
                ChinaRegions.Region province = regions.get(p);
                for (int c = 0; c < province.children.size(); c++) {
                    ChinaRegions.Region city = province.children.get(c);
                    for (int d = 0; d < city.children.size(); d++) {
                        if (saved.equals(labelFor(province, city, city.children.get(d))))
                            return new int[]{p, c, d};
                    }
                }
            }
        }
        return new int[]{0, 0, 0};
    }

    private void provinceChanged() { provinceChanged(0, 0); }

    private void provinceChanged(int cityIndex, int districtIndex) {
        selectedProvince = regions.get(provinces.selectedIndex());
        cities.setItems(names(selectedProvince.children), cityIndex);
        cityChanged(districtIndex);
    }

    private void cityChanged() { cityChanged(0); }

    private void cityChanged(int districtIndex) {
        selectedCity = selectedProvince.children.get(cities.selectedIndex());
        List<String> options = new ArrayList<>(selectedCity.children.size());
        for (ChinaRegions.Region district : selectedCity.children)
            options.add(isWholeCity(selectedCity, district)
                    ? getString(R.string.weather_whole_city) : district.name);
        districts.setItems(options, districtIndex);
    }

    private void saveDistrict() {
        if (selectedProvince == null || selectedCity == null || selectedCity.children.isEmpty()) return;
        ChinaRegions.Region district = selectedCity.children.get(districts.selectedIndex());
        WeatherStore.select(this, new WeatherApi.City(labelFor(selectedProvince, selectedCity, district),
                district.latitude, district.longitude));
        setResult(RESULT_OK);
        finish();
    }

    private static String labelFor(ChinaRegions.Region province, ChinaRegions.Region city,
                                   ChinaRegions.Region district) {
        if (isWholeCity(city, district)) return city.name;
        if (city.name.equals(province.name) || city.name.equals("省直辖县级行政区划"))
            return province.name + " · " + district.name;
        return city.name + " · " + district.name;
    }

    private static boolean isWholeCity(ChinaRegions.Region city, ChinaRegions.Region district) {
        return city.children.size() == 1 && city.name.equals(district.name)
                && city.latitude == district.latitude && city.longitude == district.longitude;
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
