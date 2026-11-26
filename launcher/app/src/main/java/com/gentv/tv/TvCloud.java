package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/** Small foreground-only client for the GenTV API; optional Umeng metrics are separate. */
final class TvCloud {
    /** Empty in builds without a configured GenTV API; every cloud feature is then off. */
    static final String BASE = BuildConfig.API_BASE_URL;
    static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("gentv-cloud", Context.MODE_PRIVATE); }
    static boolean enabled() { return !BASE.isEmpty(); }
    static HttpURLConnection open(String url) throws IOException {
        if (!enabled() || !url.startsWith(BASE)) throw new IOException("Unexpected API address");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        // Android 4.2-4.4 support TLS 1.2 but do not enable it by default.
        if (android.os.Build.VERSION.SDK_INT < 20 && c instanceof HttpsURLConnection) {
            ((HttpsURLConnection) c).setSSLSocketFactory(new LegacyTls12SocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()));
        }
        c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "GenTV/" + BuildConfig.VERSION_NAME);
        return c;
    }
    static JSONObject json(String path) throws Exception {
        HttpURLConnection c = open(BASE + path);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return new JSONObject(new String(read(c.getInputStream(), 65536), java.nio.charset.Charset.forName("UTF-8")));
        } finally { c.disconnect(); }
    }
    static JSONObject updateManifest(Context context) throws Exception {
        if (!enabled()) throw new IOException("Update service is not configured");
        if (!BuildConfig.UPDATE_FALLBACK_URL.equals("https://raw.githubusercontent.com/Elaine-Lynn/GenTV/update-manifest/config.json"))
            throw new IOException("Invalid fallback manifest address");
        return UpdateFailover.load(() -> {
            try {
                JSONObject primary = json("update?platform=tv");
                validate(primary, context);
                return primary;
            } catch (Exception failure) {
                android.util.Log.w("GenTV.Update", "Primary update manifest failed", failure);
                throw failure;
            }
        }, () -> {
            android.util.Log.w("GenTV.Update", "Primary manifest failed; requesting GitHub backup once");
            return backupUpdateManifest(context);
        });
    }
    static JSONObject backupUpdateManifest(Context context) throws Exception {
        if (!BuildConfig.UPDATE_FALLBACK_URL.equals("https://raw.githubusercontent.com/Elaine-Lynn/GenTV/update-manifest/config.json"))
            throw new IOException("Invalid backup URL");
        HttpURLConnection connection = external(BuildConfig.UPDATE_FALLBACK_URL);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("GitHub update HTTP " + connection.getResponseCode());
            JSONObject fallback = new JSONObject(new String(read(connection.getInputStream(), 65536),
                    java.nio.charset.Charset.forName("UTF-8")));
            validate(fallback, context);
            if (!fallback.getString("url").startsWith("https://github.com/Elaine-Lynn/GenTV/releases/download/"))
                throw new IOException("Backup must use a GitHub asset");
            return fallback;
        } finally { connection.disconnect(); }
    }
    static HttpURLConnection external(String url) throws IOException {
        URL parsed = new URL(url);
        if (!"https".equalsIgnoreCase(parsed.getProtocol()) || parsed.getUserInfo() != null
                || (parsed.getPort() != -1 && parsed.getPort() != 443)) throw new IOException("Invalid update host");
        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(15000);
        if (android.os.Build.VERSION.SDK_INT < 20 && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(
                    new LegacyTls12SocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()));
        }
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "GenTV/" + BuildConfig.VERSION_NAME);
        return connection;
    }
    static boolean allowedUpdateUrl(String url) {
        if (enabled() && url.startsWith(BASE + "releases/")
                && url.substring((BASE + "releases/").length()).matches("[A-Za-z0-9._-]+\\.apk")) return true;
        return url.matches("https://github\\.com/Elaine-Lynn/GenTV/releases/download/"
                + "v[0-9]+\\.[0-9]+\\.[0-9]+/gentv-tv-(?:[0-9]+\\.[0-9]+\\.[0-9]+|v[0-9]+\\.[0-9]+\\.[0-9]+-code[0-9]+)\\.apk");
    }
    static HttpURLConnection openUpdate(String url) throws IOException {
        if (!allowedUpdateUrl(url)) throw new IOException("Unexpected update URL");
        if (url.startsWith(BASE + "releases/")) return open(url);
        return followGithubRelease(url);
    }
    private static HttpURLConnection followGithubRelease(String url) throws IOException {
        for (int hop = 0; hop < 4; hop++) {
            URI uri;
            try { uri = URI.create(url); } catch (IllegalArgumentException bad) { throw new IOException("Invalid redirect", bad); }
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !("github.com".equalsIgnoreCase(host)
                    || "release-assets.githubusercontent.com".equalsIgnoreCase(host)
                    || "objects.githubusercontent.com".equalsIgnoreCase(host)))
                throw new IOException("Unexpected update redirect");
            HttpURLConnection connection = external(url);
            int status;
            try { status = connection.getResponseCode(); }
            catch (IOException failure) { connection.disconnect(); throw failure; }
            if (status == 200) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if ((status != 301 && status != 302 && status != 303 && status != 307 && status != 308)
                    || location == null) throw new IOException("Update download HTTP " + status);
            try { url = uri.resolve(location).toString(); }
            catch (IllegalArgumentException bad) { throw new IOException("Invalid update redirect", bad); }
        }
        throw new IOException("Too many update redirects");
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
        if (!allowedUpdateUrl(url) || !update.getString("sha256").matches("[a-fA-F0-9]{64}"))
            throw new IOException("Invalid release");
        if (url.startsWith("https://github.com/")) {
            String name = update.getString("versionName");
            String prefix = "https://github.com/Elaine-Lynn/GenTV/releases/download/v" + name + "/";
            if (!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !(url.equals(prefix + "gentv-tv-" + name + ".apk")
                    || url.equals(prefix + "gentv-tv-v" + name + "-code" + version + ".apk")))
                throw new IOException("GitHub release name mismatch");
        }
        if (update.optLong("sizeBytes", 0) < 0 || update.optLong("sizeBytes", 0) > UpdatePackage.MAX_APK_BYTES)
            throw new IOException("Invalid release size");
        if (!UpdatePolicy.compatible(BuildConfig.VERSION_CODE, version, update.optInt("minSdk",26), android.os.Build.VERSION.SDK_INT))
            throw new IOException("Unsupported Android");
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
                JSONObject update = updateManifest(activity);
                remember(activity,update);
                long latest=update.getLong("versionCode");
                boolean required=UpdatePolicy.required(BuildConfig.VERSION_CODE,latest,update.optLong("minSupportedVersionCode",0),update.optBoolean("forceUpdate",false));

                if (latest <= BuildConfig.VERSION_CODE) return;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) return;
                    activity.startActivity(new android.content.Intent(activity, CloudSettingsActivity.class).putExtra("check",true));
                });
            } catch (Exception error) { android.util.Log.w("GenTV.Update", "Both update checks failed", error); }
        });
    }
}
