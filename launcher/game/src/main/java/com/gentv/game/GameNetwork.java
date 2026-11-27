package com.gentv.game;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Foundation-owned network transport. Host configuration supplies the optional service address. */
final class GameNetwork {
    final String api, fallback;

    GameNetwork(String api, String fallback) throws IOException {
        if (api == null) api = "";
        if (!api.isEmpty() && !api.matches("https://[A-Za-z0-9.-]+(:[0-9]+)?/([A-Za-z0-9._~-]+/)*"))
            throw new IOException("Invalid game API base");
        if (!"https://raw.githubusercontent.com/Elaine-Lynn/GenTV/update-manifest/game-runtime/".equals(fallback))
            throw new IOException("Invalid game catalog backup");
        this.api = api;
        this.fallback = fallback;
    }

    interface Validator { void validate(JSONObject value) throws Exception; }
    JSONObject catalog() throws Exception {
        return jsonWithBackup("plugins/games/catalog", "catalog.json", value -> GameListing.parse(value));
    }
    JSONObject manifest(String id, Validator validator) throws Exception {
        if (!GameListing.validId(id)) throw new IOException("Invalid game ID");
        return jsonWithBackup("plugins/games/" + id + "/update", "games/" + id + "/latest/manifest.json", validator);
    }
    private JSONObject jsonWithBackup(String path, String backupPath, Validator validator) throws Exception {
        try {
            if (api.isEmpty()) throw new IOException("Game API is not configured");
            JSONObject value = json(api + path); validator.validate(value); return value;
        } catch (Exception primary) {
            try { JSONObject value = json(fallback + backupPath); validator.validate(value); return value; }
            catch (Exception backup) { backup.addSuppressed(primary); throw backup; }
        }
    }
    private JSONObject json(String url) throws Exception {
        HttpURLConnection connection = connect(url);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Game service HTTP " + connection.getResponseCode());
            return new JSONObject(new String(read(connection.getInputStream(), 128 * 1024), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }
    static HttpURLConnection connect(String value) throws IOException {
        URL url = new URL(value);
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || url.getRef() != null)
            throw new IOException("HTTPS game address required");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "GenTV-GameFoundation/2");
        return connection;
    }
    static HttpURLConnection download(String url) throws IOException {
        // Initial URLs have already been pinned by GameContentManifest.
        if (!url.startsWith("https://github.com/")) return connect(url);
        for (int i = 0; i < 4; i++) {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443
                    || !("github.com".equals(host) || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host))) throw new IOException("Unexpected game redirect");
            HttpURLConnection connection = connect(url);
            int code;
            try { code = connection.getResponseCode(); }
            catch (IOException failed) { connection.disconnect(); throw failed; }
            if (code == 200) return connection;
            String next = connection.getHeaderField("Location");
            connection.disconnect();
            if (next == null || !(code == 301 || code == 302 || code == 303 || code == 307 || code == 308))
                throw new IOException("Game download HTTP " + code);
            url = uri.resolve(next).toString();
        }
        throw new IOException("Too many game redirects");
    }
    static byte[] read(InputStream stream, int maximum) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maximum) throw new IOException("Game response too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
