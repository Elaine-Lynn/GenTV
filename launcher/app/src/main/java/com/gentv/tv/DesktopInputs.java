package com.gentv.tv;

import android.content.ComponentName;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional input shortcuts. The original MainActivity remains responsible for switching/fallbacks. */
final class DesktopInputs {
    static Map<String, String> list(Context ctx) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            TvInputManager manager = (TvInputManager)ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (manager != null) for (TvInputInfo input : manager.getTvInputList()) {
                if (input.isPassthroughInput()) result.put(input.getId(), InputNames.tidy(input.loadLabel(ctx)));
            }
        } catch (RuntimeException ignored) { /* No backend: apps and the original source menu still work. */ }
        return result;
    }
    static ComponentName component(Context ctx, String id) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("source.");
            for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return new ComponentName(ctx.getPackageName(), hex.toString());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String label(JSONObject profile, String id, String fallback) {
        JSONObject names = profile.optJSONObject("sourceNames");
        return names == null ? fallback : names.optString(id, fallback);
    }
    static String target(JSONObject profile, String id) {
        JSONObject targets = profile.optJSONObject("sourceTargets");
        return targets == null ? id : targets.optString(id, id);
    }
    static JSONArray sources(Context ctx) throws Exception {
        JSONArray rows = new JSONArray();
        for (Map.Entry<String, String> e : list(ctx).entrySet()) rows.put(new JSONObject().put("id", e.getKey()).put("label", e.getValue()));
        return rows;
    }
    static android.graphics.drawable.Drawable icon(Context ctx, JSONObject state, String id) {
        // Generic original vector artwork: no third-party logos or extra bitmap allocations.
        String kind = state.optJSONObject("sourceDevices").optString(id, "");
        int resource = kind.equals("computer") ? R.drawable.ic_picture :
                (kind.equals("playstation") || kind.equals("switch") || kind.equals("xbox")) ? R.drawable.ic_apps : R.drawable.ic_input;
        return ctx.getDrawable(resource);
    }
}
