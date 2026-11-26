package com.gentv.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.Charset;

/** Validated catalog snapshots; never mix successful responses from different API servers. */
final class WallpaperCatalogSnapshot {
    static final int MAX_BYTES = 96 * 1024;

    private WallpaperCatalogSnapshot() { }

    static JSONArray categories(JSONObject response) throws Exception {
        JSONArray groups = response.getJSONArray("categories");
        if (groups.length() > 100) throw new IOException("Too many wallpaper categories");
        int total = 0;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            if (group.getString("id").isEmpty()) throw new IOException("Missing category ID");
            JSONArray items = group.getJSONArray("items");
            total += items.length();
            if (total > 500) throw new IOException("Too many wallpapers");
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.getJSONObject(j);
                String kind = item.getString("kind");
                if (item.getString("id").isEmpty() || !("image".equals(kind) || "video".equals(kind)
                        || "placeholder".equals(kind) || "shuffle".equals(kind)))
                    throw new IOException("Invalid wallpaper item");
            }
        }
        return groups;
    }

    static String encode(String endpoint, JSONObject response, long fetchedAt) throws Exception {
        if (endpoint.isEmpty()) throw new IOException("Wallpaper API is not configured");
        categories(response);
        String encoded = new JSONObject().put("endpoint", endpoint).put("fetchedAt", fetchedAt)
                .put("catalog", response).toString();
        if (encoded.getBytes(Charset.forName("UTF-8")).length > MAX_BYTES)
            throw new IOException("Wallpaper catalog is too large");
        return encoded;
    }

    static JSONArray decode(String endpoint, String encoded) throws Exception {
        if (endpoint.isEmpty() || encoded.getBytes(Charset.forName("UTF-8")).length > MAX_BYTES)
            throw new IOException("Invalid wallpaper snapshot");
        JSONObject snapshot = new JSONObject(encoded);
        if (!endpoint.equals(snapshot.getString("endpoint"))) throw new IOException("Different wallpaper API");
        return categories(snapshot.getJSONObject("catalog"));
    }
}
