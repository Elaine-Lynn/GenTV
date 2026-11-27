package com.gentv.game;

import com.gentv.game.api.GameContentEntry;
import org.json.JSONObject;
import java.io.IOException;

/** Validation is shared by fresh downloads and previously saved, independently versioned games. */
final class GameContentManifest {
    static final long MAX_BYTES = 32L * 1024 * 1024;
    final long version, size;
    final String versionName, name, url, hash;
    final JSONObject json;

    GameContentManifest(JSONObject json, GameListing game, long baseVersion, String certificate,
                        String apiBase) throws Exception {
        this.json = json;
        version = json.getLong("versionCode");
        versionName = json.getString("versionName");
        long minimum = json.getLong("minBaseVersionCode"), maximum = json.getLong("maxBaseVersionCode");
        if (version <= 0 || !versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || json.getInt("baseApiVersion") != GameContentEntry.API_VERSION
                || minimum <= 0 || maximum < minimum || baseVersion < minimum || baseVersion > maximum
                || !game.id.equals(json.getString("id"))
                || !game.packageName.equals(json.getString("packageName"))
                || !game.entryClass.equals(json.getString("entryClass"))
                || !certificate.matches("[a-fA-F0-9]{64}")
                || !certificate.equalsIgnoreCase(json.getString("signingCertificateSha256")))
            throw new IOException("Game plugin is incompatible with this foundation");
        JSONObject plugin = json.getJSONObject("plugin");
        name = plugin.getString("name");
        url = plugin.getString("url");
        hash = plugin.getString("sha256");
        size = plugin.getLong("sizeBytes");
        String expected = "game_" + game.id + ".apk";
        String github = "https://github.com/Elaine-Lynn/GenTV/releases/download/game-"
                + game.id + "-v" + versionName + "/" + expected;
        if (!expected.equals(name) || !hash.matches("[a-fA-F0-9]{64}") || size <= 0 || size > MAX_BYTES
                || !(github.equals(url) || !apiBase.isEmpty() && (apiBase + "releases/" + expected).equals(url)))
            throw new IOException("Invalid game download metadata");
    }
}
