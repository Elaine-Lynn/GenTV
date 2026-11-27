package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Links received on this TV, stored with their locally generated share codes. */
final class ShareCodeLinks {
    static final int CODE_LENGTH = 6;
    static final int NAME_LIMIT = 15;
    static final int MAX_LINKS = 4;
    private static final int URL_LIMIT = 2048;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String ENTRIES = "entries";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SharedPreferences prefs;

    ShareCodeLinks(Context context) {
        prefs = context.getSharedPreferences("share_code_links", Context.MODE_PRIVATE);
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
            String code = nextCode(previous);
            try {
                JSONArray updated = new JSONArray();
                updated.put(new JSONObject().put("code", code).put("name", name).put("url", url));
                for (int index = 0; index < previous.length(); index++) updated.put(previous.get(index));
                write(updated);
                return new Link(code, name, url);
            } catch (JSONException error) {
                throw new IOException("Could not encode shared link", error);
            }
        }
    }

    void delete(String code) throws IOException {
        synchronized (ShareCodeLinks.class) {
            JSONArray previous = read();
            JSONArray updated = new JSONArray();
            for (int index = 0; index < previous.length(); index++) {
                JSONObject item = previous.optJSONObject(index);
                if (item != null && !code.equals(item.optString("code"))) updated.put(item);
            }
            write(updated);
        }
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

    private static String nextCode(JSONArray previous) {
        while (true) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String value = code.toString();
            boolean used = false;
            for (int index = 0; index < previous.length() && !used; index++) {
                JSONObject item = previous.optJSONObject(index);
                used = item != null && value.equals(item.optString("code"));
            }
            if (!used) return value;
        }
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
        return code.substring(0, 3) + "–" + code.substring(3);
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
