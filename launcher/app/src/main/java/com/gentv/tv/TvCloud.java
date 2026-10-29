package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

/** Small foreground-only client; no services, device identifiers or third-party SDKs. */
final class TvCloud {
    /** Empty in builds without a configured GenTV API; every cloud feature is then off. */
    static final String BASE = BuildConfig.API_BASE_URL;
    static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("gentv-cloud", Context.MODE_PRIVATE); }
    static boolean enabled() { return !BASE.isEmpty(); }
    static HttpURLConnection open(String url) throws IOException {
        if (!enabled() || !url.startsWith(BASE)) throw new IOException("Unexpected API address");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "GenTV/" + BuildConfig.VERSION_NAME);
        return c;
    }
    static JSONObject json(String path) throws Exception {
        HttpURLConnection c = open(BASE + path);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return new JSONObject(new String(read(c.getInputStream(), 65536), java.nio.charset.StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }
    static byte[] read(InputStream in, int limit) throws IOException {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = source.read(buf)) != -1) { if (out.size() + n > limit) throw new IOException("Response too large"); out.write(buf, 0, n); }
            return out.toByteArray();
        }
    }
    static void validate(JSONObject update, Context context) throws Exception {
        long version=update.getLong("versionCode");
        UpdatePolicy.required(BuildConfig.VERSION_CODE,version,update.optLong("minSupportedVersionCode",0),update.optBoolean("forceUpdate",false));
        if (!context.getPackageName().equals(update.getString("packageName"))) throw new IOException("Wrong package");
        String url=update.getString("url");
        if (!url.startsWith(BASE+"releases/") || !url.substring(BASE.length()+9).matches("[A-Za-z0-9._-]+\\.apk")
                || !update.getString("sha256").matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid release");
        if (update.optInt("minSdk",26)>android.os.Build.VERSION.SDK_INT) throw new IOException("Unsupported Android");
        String certificate=update.optString("signingCertificateSha256", "");
        if (!certificate.equalsIgnoreCase(UpdatePackage.certificate(context))) throw new IOException("Incompatible signing certificate");
    }
    static boolean required(Context context) {
        // A mandatory policy is a bounded lease: a broken/revoked release cannot lock an offline TV forever.
        return prefs(context).getLong("requiredUntil",0)>System.currentTimeMillis()
                && prefs(context).getLong("requiredVersion",0)>BuildConfig.VERSION_CODE;
    }
    static void remember(Context context, JSONObject update) throws Exception {
        validate(update,context);
        long latest=update.getLong("versionCode");
        boolean required=UpdatePolicy.required(BuildConfig.VERSION_CODE,latest,update.optLong("minSupportedVersionCode",0),update.optBoolean("forceUpdate",false));
        prefs(context).edit().putLong("requiredVersion",required?latest:0)
                .putLong("requiredUntil",required?System.currentTimeMillis()+86400000L:0)
                .putLong("updateSuccess",System.currentTimeMillis()).apply();
    }
    static void checkOnHome(android.app.Activity activity) {
        if (!enabled()) return;
        if (required(activity)) {
            activity.startActivity(new android.content.Intent(activity,CloudSettingsActivity.class).putExtra("check",true)); return;
        }
        long now = System.currentTimeMillis();
        if (now - prefs(activity).getLong("updateSuccess", 0) < 86400000L
                || now - prefs(activity).getLong("updateAttempt", 0) < 300000L) return;
        prefs(activity).edit().putLong("updateAttempt", now).apply();
        IO.execute(() -> {
            try {
                JSONObject update = json("update?platform=tv");
                remember(activity,update);
                long latest=update.getLong("versionCode");
                boolean required=UpdatePolicy.required(BuildConfig.VERSION_CODE,latest,update.optLong("minSupportedVersionCode",0),update.optBoolean("forceUpdate",false));

                if (latest <= BuildConfig.VERSION_CODE) return;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) return;
                    activity.startActivity(new android.content.Intent(activity, CloudSettingsActivity.class).putExtra("check",true));
                });
            } catch (Exception ignored) { }
        });
    }
    static void session(Context context, long seconds) {
        Context app = context.getApplicationContext();
        if (!enabled() || !prefs(app).getBoolean("statistics", false)) return;
        IO.execute(() -> {
            if (!prefs(app).getBoolean("statistics", false)) return;
            HttpURLConnection c = null;
            try {
                JSONObject event = new JSONObject().put("event", "session").put("versionCode", BuildConfig.VERSION_CODE)
                        .put("foregroundSeconds", Math.min(86400, Math.max(0, seconds)));
                c = open(BASE + "stats"); c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) { out.write(event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                c.getResponseCode();
            } catch (Exception ignored) { /* Offline statistics are dropped, never queued. */ }
            finally { if (c != null) c.disconnect(); }
        });
    }
}
