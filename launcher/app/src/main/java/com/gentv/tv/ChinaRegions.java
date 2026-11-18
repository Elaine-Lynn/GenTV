package com.gentv.tv;

import android.content.Context;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mainland province, city and district centres bundled for offline selection. */
final class ChinaRegions {
    static final class Region {
        final String name;
        final double longitude;
        final double latitude;
        final List<Region> children;

        Region(String name, double longitude, double latitude, List<Region> children) {
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
            this.children = children;
        }
    }

    static List<Region> load(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("china_regions.json")) {
            JSONArray data = new JSONArray(new String(TvCloud.read(input, 256 * 1024), Charset.forName("UTF-8")));
            List<Region> provinces = parse(data);
            if (provinces.size() != 31) throw new IOException("Incomplete China regions data");
            return provinces;
        }
    }

    private static List<Region> parse(JSONArray data) throws Exception {
        List<Region> regions = new ArrayList<>(data.length());
        for (int i = 0; i < data.length(); i++) {
            JSONArray item = data.getJSONArray(i);
            String name = item.getString(0);
            double longitude = item.getDouble(1);
            double latitude = item.getDouble(2);
            if (name.isEmpty() || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                throw new IOException("Invalid China region");
            }
            regions.add(new Region(name, longitude, latitude,
                    item.length() > 3 ? parse(item.getJSONArray(3)) : Collections.emptyList()));
        }
        return regions;
    }
}
