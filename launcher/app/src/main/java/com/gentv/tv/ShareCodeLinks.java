package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Links received on this TV and their share codes. With the GenTV API configured the server issues
 * each code, so any TV can open it; this TV keeps its own codes for the saved cards. Builds without
 * the API fall back to codes that only this TV knows.
 */
final class ShareCodeLinks {
    static final int CODE_LENGTH = 4;
    static final int NAME_LIMIT = 15;
    static final int MAX_LINKS = 4;
    private static final int URL_LIMIT = 2048;
    private static final String DIGITS = "0123456789";
    // I and O are left out so a letter is never mistaken for 1 or 0.
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String ENTRIES = "entries";
    private static final String OWNER = "owner";
    /** Marks a link whose code the server issued; older links are registered on the next sync. */
    private static final String REMOTE = "remote";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final SharedPreferences prefs;

    ShareCodeLinks(Context context) {
        prefs = context.getSharedPreferences("share_code_links", Context.MODE_PRIVATE);
        migrateCodes();
    }

    /** Gives links saved by a build with longer codes a code that the keypad can enter. */
    private void migrateCodes() {
        synchronized (ShareCodeLinks.class) {
            JSONArray data = read();
            Set<String> used = new HashSet<>();
            List<JSONObject> stale = new ArrayList<>();
            for (int index = 0; index < data.length(); index++) {
                JSONObject item = data.optJSONObject(index);
                if (item == null) continue;
                String code = item.optString("code");
                if (!validCode(code) || !used.add(code)) stale.add(item);
            }
            if (stale.isEmpty()) return;
            try {
                for (JSONObject item : stale) {
                    String code = nextCode(used, RANDOM);
                    used.add(code);
                    item.put("code", code);
                }
                write(data);
            } catch (JSONException | IOException ignored) {
                // Saved cards still open their links; the codes are rewritten on the next start.
            }
        }
    }

    List<Link> list() {
        List<Link> links = new ArrayList<>();
        JSONArray data = read();
        for (int index = 0; index < data.length(); index++) {
            JSONObject item = data.optJSONObject(index);
            if (item != null) {
                links.add(new Link(item.optString("code"), item.optString("name"), item.optString("url")));
            }
        }
        return links;
    }

    Link find(String code) {
        for (Link link : list()) {
            if (link.code.equals(code)) return link;
        }
        return null;
    }

    boolean hasRoom() {
        return read().length() < MAX_LINKS;
    }

    Link add(String name, String url) throws IOException {
        // Check capacity and allocate the code from the same snapshot across receiver instances.
        synchronized (ShareCodeLinks.class) {
            JSONArray previous = read();
            if (previous.length() >= MAX_LINKS) return null;
            boolean remote = TvCloud.enabled();
            String code = remote ? register(name, url) : nextCode(codes(previous), RANDOM);
            if (code == null) return null;
            try {
                JSONArray updated = new JSONArray();
                updated.put(new JSONObject().put("code", code).put("name", name).put("url", url)
                        .put(REMOTE, remote));
                for (int index = 0; index < previous.length(); index++) updated.put(previous.get(index));
                write(updated);
                return new Link(code, name, url);
            } catch (JSONException error) {
                throw new IOException("Could not encode shared link", error);
            }
        }
    }

    /** Thrown when the GenTV API cannot be reached or refuses for a reason other than a full TV. */
    static final class ServerException extends IOException {
        ServerException(String message) { super(message); }
    }

    /** Asks the server for a code; null when it already holds four codes for this TV. */
    private String register(String name, String url) throws IOException {
        try {
            JSONObject body = new JSONObject().put("owner", owner()).put("name", name).put("url", url);
            HttpURLConnection connection = TvCloud.open(TvCloud.BASE + "share-codes");
            try {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] payload = body.toString().getBytes(UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
                int status = connection.getResponseCode();
                if (status == 409) return null;
                if (status != 201) throw new ServerException("HTTP " + status);
                String code = new JSONObject(new String(TvCloud.read(connection.getInputStream(), 8192), UTF_8))
                        .getString("code");
                if (!validCode(code)) throw new ServerException("Unexpected code");
                return code;
            } finally {
                connection.disconnect();
            }
        } catch (JSONException error) {
            throw new ServerException("Unexpected response");
        } catch (ServerException error) {
            throw error;
        } catch (IOException error) {
            throw new ServerException(error.getClass().getSimpleName());
        }
    }

    /** Looks a code up on the server; null when no TV has it. Runs off the main thread. */
    static Link lookup(String code) throws IOException {
        if (!TvCloud.enabled() || !validCode(code)) return null;
        HttpURLConnection connection = TvCloud.open(TvCloud.BASE + "share-codes/" + code);
        try {
            int status = connection.getResponseCode();
            if (status == 404) return null;
            if (status != 200) throw new ServerException("HTTP " + status);
            JSONObject body = new JSONObject(new String(TvCloud.read(connection.getInputStream(), 8192), UTF_8));
            String name = body.optString("name");
            String url = webUrl(body.optString("url"));
            if (!code.equals(body.optString("code")) || !validName(name) || url == null) {
                throw new ServerException("Unexpected response");
            }
            return new Link(code, name, url);
        } catch (JSONException error) {
            throw new ServerException("Unexpected response");
        } finally {
            connection.disconnect();
        }
    }

    /** Registers links saved before codes came from the server; true when a code changed. Off the main thread. */
    boolean syncLocalLinks() {
        if (!TvCloud.enabled()) return false;
        synchronized (ShareCodeLinks.class) {
            JSONArray data = read();
            boolean changed = false;
            for (int index = 0; index < data.length(); index++) {
                JSONObject item = data.optJSONObject(index);
                if (item == null || item.optBoolean(REMOTE)) continue;
                try {
                    String code = register(item.optString("name"), item.optString("url"));
                    if (code == null) break;
                    item.put("code", code).put(REMOTE, true);
                    changed = true;
                } catch (IOException | JSONException error) {
                    break;
                }
            }
            if (!changed) return false;
            try {
                write(data);
                return true;
            } catch (IOException error) {
                return false;
            }
        }
    }

    /** Removes a link here and, for a server-issued code, on the server. Runs off the main thread. */
    void delete(String code) throws IOException {
        synchronized (ShareCodeLinks.class) {
            JSONArray previous = read();
            JSONArray updated = new JSONArray();
            boolean remote = false;
            for (int index = 0; index < previous.length(); index++) {
                JSONObject item = previous.optJSONObject(index);
                if (item == null) continue;
                if (code.equals(item.optString("code"))) remote |= item.optBoolean(REMOTE);
                else updated.put(item);
            }
            if (remote) unregister(code);
            write(updated);
        }
    }

    private void unregister(String code) throws IOException {
        HttpURLConnection connection = TvCloud.open(TvCloud.BASE + "share-codes/" + code);
        try {
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("X-GenTV-Owner", owner());
            int status = connection.getResponseCode();
            // 404: already gone on the server, so only this TV's copy is left to remove.
            if (status != 204 && status != 404) throw new ServerException("HTTP " + status);
        } finally {
            connection.disconnect();
        }
    }

    /** This TV's secret proving which share codes it created; only its SHA-256 reaches the server's disk. */
    private String owner() {
        String owner = prefs.getString(OWNER, null);
        if (owner != null && owner.matches("[0-9a-f]{64}")) return owner;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value));
        owner = hex.toString();
        prefs.edit().putString(OWNER, owner).commit();
        return owner;
    }

    private void write(JSONArray data) throws IOException {
        if (!prefs.edit().putString(ENTRIES, data.toString()).commit()) {
            throw new IOException("Could not write shared links");
        }
    }

    private JSONArray read() {
        try {
            return new JSONArray(prefs.getString(ENTRIES, "[]"));
        } catch (JSONException error) {
            return new JSONArray();
        }
    }

    private static Set<String> codes(JSONArray data) {
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < data.length(); index++) {
            JSONObject item = data.optJSONObject(index);
            if (item != null) codes.add(item.optString("code"));
        }
        return codes;
    }

    static boolean validCode(String code) {
        if (code.length() != CODE_LENGTH) return false;
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            if (DIGITS.indexOf(character) < 0 && LETTERS.indexOf(character) < 0) return false;
        }
        return true;
    }

    /**
     * Four digits first; only once every all-digit code is taken does one position become a letter,
     * then two, and so on up to four letters.
     */
    static String nextCode(Set<String> used, Random random) {
        for (int letters = 0; letters <= CODE_LENGTH; letters++) {
            long capacity = combinations(CODE_LENGTH, letters) * pow(DIGITS.length(), CODE_LENGTH - letters)
                    * pow(LETTERS.length(), letters);
            int taken = 0;
            for (String code : used) if (validCode(code) && letterCount(code) == letters) taken++;
            if (taken >= capacity) continue;
            while (true) {
                String code = randomCode(letters, random);
                if (!used.contains(code)) return code;
            }
        }
        throw new IllegalStateException("No share code left");
    }

    private static String randomCode(int letters, Random random) {
        char[] code = new char[CODE_LENGTH];
        boolean[] letter = new boolean[CODE_LENGTH];
        for (int placed = 0; placed < letters; ) {
            int position = random.nextInt(CODE_LENGTH);
            if (!letter[position]) { letter[position] = true; placed++; }
        }
        for (int index = 0; index < CODE_LENGTH; index++) {
            String source = letter[index] ? LETTERS : DIGITS;
            code[index] = source.charAt(random.nextInt(source.length()));
        }
        return new String(code);
    }

    static int letterCount(String code) {
        int count = 0;
        for (int index = 0; index < code.length(); index++) if (LETTERS.indexOf(code.charAt(index)) >= 0) count++;
        return count;
    }

    private static long combinations(int n, int k) {
        long result = 1;
        for (int index = 1; index <= k; index++) result = result * (n - k + index) / index;
        return result;
    }

    private static long pow(int base, int exponent) {
        long result = 1;
        for (int index = 0; index < exponent; index++) result *= base;
        return result;
    }

    static boolean validName(String name) {
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_LIMIT) return false;
        for (int index = 0; index < name.length(); index++) {
            if (Character.isISOControl(name.charAt(index))) return false;
        }
        return true;
    }

    static String webUrl(String value) {
        if (value.length() > URL_LIMIT) return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535) return null;
            return scheme.toLowerCase(Locale.ROOT) + uri.toASCIIString().substring(scheme.length());
        } catch (URISyntaxException error) {
            return null;
        }
    }

    static String formatted(String code) {
        return code;
    }

    static final class Link {
        final String code;
        final String name;
        final String url;

        Link(String code, String name, String url) {
            this.code = code;
            this.name = name;
            this.url = url;
        }
    }
}
