package com.gentv.tv;

import android.content.Context;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared, bundled model routes for standalone TV installations. Explicit config.json values win. */
final class InputProfiles {
    private static JSONArray profiles;

    static synchronized JSONObject forDevice(Context ctx) {
        try {
            if (profiles == null) {
                try (java.io.InputStream in = ctx.getAssets().open("source-profiles.json")) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    profiles = new JSONArray(out.toString("UTF-8"));
                }
            }
            JSONObject selected = null;
            String major = Build.VERSION.RELEASE.split("\\.")[0];
            for (int n = 0; n < profiles.length(); n++) {
                JSONObject row = profiles.getJSONObject(n);
                JSONArray versions = row.optJSONArray("android");
                boolean version = versions == null || versions.length() == 0;
                if (versions != null) for (int v = 0; v < versions.length(); v++) {
                    if (major.equals(versions.getString(v))) version = true;
                }
                if (!version || !InputProfileMatch.matches(row.getString("id"), row.getString("title"),
                        Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.BRAND, Build.MANUFACTURER)) continue;
                JSONObject config = row.getJSONObject("config");
                if (selected != null && !canonical(selected).equals(canonical(config))) return new JSONObject();
                selected = config;
            }
            return selected == null ? new JSONObject() : selected;
        } catch (java.io.IOException | org.json.JSONException | RuntimeException e) {
            android.util.Log.w("GenTV.InputProfiles", "Cannot select model input routing", e);
            return new JSONObject();
        }
    }

    private static String canonical(JSONObject config) throws org.json.JSONException {
        java.util.TreeMap<String, String> values = new java.util.TreeMap<>();
        java.util.Iterator<String> keys = config.keys();
        while (keys.hasNext()) {
            String key = keys.next(); values.put(key, String.valueOf(config.get(key)));
        }
        return values.toString();
    }
}
