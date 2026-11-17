package com.gentv.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;

/** Opt-in schema 3: optional factory MAC, installation ID fallback, no app names or input text. */
public final class TvMetrics extends Application implements Application.ActivityLifecycleCallbacks {
    private static TvMetrics instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MetricsClock clock = new MetricsClock();
    private final ArrayDeque<JSONObject> pending = new ArrayDeque<>();
    private Activity visible;
    private volatile String session = UUID.randomUUID().toString();
    private int sequence;
    private long lastPause;
    private boolean sending;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (visible == null) return;
            duration("heartbeat"); flush();
            handler.postDelayed(this, 60000);
        }
    };
    @Override public void onCreate() { super.onCreate(); instance = this; registerActivityLifecycleCallbacks(this); }
    static boolean enabled(Context c) { return TvCloud.enabled() && TvCloud.prefs(c).getBoolean("metrics_v3", false); }
    static boolean emulator() {
        return Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("sdk_gphone") || Build.MODEL.contains("Emulator") || Build.PRODUCT.contains("sdk");
    }
    static void consent(Context c, boolean enabled) {
        // Preserve an explicit user choice independently of the default.
        TvCloud.prefs(c).edit().putBoolean("metrics_v3", enabled).putBoolean("metrics_v2", false).putBoolean("statistics", false).apply();
        if (!enabled) TvCloud.prefs(c).edit().remove("metrics_install").apply();
        if (instance == null) return;
        instance.pending.clear(); instance.session = UUID.randomUUID().toString(); instance.sequence = 0;
        if (enabled && instance.visible != null) {
            instance.clock.start(SystemClock.elapsedRealtime());
            instance.event("app_open", "", 0); instance.event("screen_view", screen(instance.visible), 0); instance.flush();
        } else instance.clock.stop();
    }
    static void action(Context c, String value) {
        if (instance != null && enabled(c)) { instance.event("action", value, 0); instance.flush(); }
    }
    private static String screen(Activity a) {
        if (a instanceof MainActivity) return "home";
        if (a instanceof LauncherSettingsActivity || a instanceof HomeSettingsActivity) return "launcher_settings";
        if (a instanceof QuickSettingsActivity) return "quick_settings";
        if (a instanceof CloudSettingsActivity) return "update";
        if (a instanceof MetricsSettingsActivity) return "privacy";
        return "other";
    }

    private void event(String name, String value, int seconds) {
        if (!enabled(this)) return;
        try {
            while (pending.size() >= 100) pending.removeFirst();
            pending.addLast(new JSONObject().put("event_id", UUID.randomUUID().toString())
                    .put("occurred_at", System.currentTimeMillis()).put("sequence", sequence++)
                    .put("name", name).put("value", value).put("foreground_seconds", seconds));
        } catch (Exception ignored) { }
    }
    private void duration(String name) { if (enabled(this)) event(name, "", clock.sample(SystemClock.elapsedRealtime())); }
    private void flush() {
        if (sending || pending.isEmpty() || !enabled(this)) return;
        try {
            while (!pending.isEmpty() && System.currentTimeMillis()-pending.peekFirst().optLong("occurred_at") > 86400000L) pending.removeFirst();
            if (pending.isEmpty()) return;
            String install = TvCloud.prefs(this).getString("metrics_install", "");
            if (install.isEmpty()) {
                install = UUID.randomUUID().toString();
                TvCloud.prefs(this).edit().putString("metrics_install", install).apply();
            }
            JSONArray events = new JSONArray();
            for (JSONObject e : pending) { if (events.length() >= 50) break; events.put(e); }
            String current = session;
            String body = new JSONObject().put("schema_version", 3).put("app", "gentv_tv")
                    .put("app_version", BuildConfig.VERSION_NAME).put("android_sdk", Build.VERSION.SDK_INT)
                    .put("install_id", install).put("mac_address", DeviceMac.read()).put("session_id", current)
                    .put("traffic", BuildConfig.DEBUG || emulator() || TvCloud.prefs(this).getBoolean("metrics_test", false) ? "test" : "production")
                    .put("events", events).toString();
            sending = true;
            TvCloud.IO.execute(() -> {
                boolean accepted = false;
                if (enabled(this) && current.equals(session)) {
                    HttpURLConnection c = null;
                    try {
                        c = TvCloud.open(TvCloud.BASE + "analytics"); c.setRequestMethod("POST"); c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        try (java.io.OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
                        accepted = c.getResponseCode() == 204;
                    } catch (Exception ignored) { }
                    finally { if (c != null) c.disconnect(); }
                }
                boolean ok = accepted;
                handler.post(() -> {
                    sending = false;
                    if (ok && current.equals(session)) {
                        for (int i = 0; i < events.length(); i++) pending.remove(events.optJSONObject(i));
                        flush();
                    } else if (!current.equals(session)) flush();
                });
            });
        } catch (Exception ignored) { sending = false; }
    }
    @Override public void onActivityResumed(Activity a) {
        if (a instanceof AerialScreensaverActivity) return; // passive playback is not active usage
        visible = a;
        if (enabled(this)) {
            if (lastPause == 0 || SystemClock.elapsedRealtime()-lastPause >= 1800000) {
                pending.clear(); session = UUID.randomUUID().toString(); sequence = 0; event("app_open", "", 0);
            }
            clock.start(SystemClock.elapsedRealtime()); event("screen_view", screen(a), 0); flush();
        }
        handler.removeCallbacks(heartbeat); handler.postDelayed(heartbeat, 60000);
    }
    @Override public void onActivityPaused(Activity a) {
        if (visible != a) return;
        duration("session_duration"); clock.stop(); lastPause = SystemClock.elapsedRealtime(); visible = null;
        handler.removeCallbacks(heartbeat); flush();
    }
    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
