package com.gentv.game;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameContentManifestTest {
    private static final String CERT = "a".repeat(64), HASH = "b".repeat(64);
    private GameListing listing() throws Exception {
        return new GameListing(new JSONObject("{id:'kart',title:'Kart',packageName:'com.gentv.game.kart.plugin',entryClass:'com.gentv.game.kart.KartGamePlugin'}"));
    }
    private JSONObject manifest() throws Exception {
        JSONObject value = new JSONObject();
        value.put("id", "kart").put("versionCode", 7).put("versionName", "1.1.0")
            .put("baseApiVersion", 1).put("minBaseVersionCode", 100).put("maxBaseVersionCode", 200)
            .put("packageName", "com.gentv.game.kart.plugin").put("entryClass", "com.gentv.game.kart.KartGamePlugin")
            .put("signingCertificateSha256", CERT);
        value.put("plugin", new JSONObject().put("name", "game_kart.apk").put("sha256", HASH).put("sizeBytes", 1024)
            .put("url", "https://github.com/Elaine-Lynn/GenTV/releases/download/game-kart-v1.1.0/game_kart.apk"));
        return value;
    }
    private void reject(JSONObject json, long base) throws Exception {
        try { new GameContentManifest(json, listing(), base, CERT, ""); fail("Invalid game must be rejected"); }
        catch (java.io.IOException expected) { }
    }
    @Test public void independentGameVersionAndFoundationCompatibility() throws Exception {
        assertEquals(7, new GameContentManifest(manifest(), listing(), 100, CERT, "").version);
        assertEquals(7, new GameContentManifest(manifest(), listing(), 150, CERT, "").version);
        reject(manifest(), 99); reject(manifest(), 201);
        reject(manifest().put("baseApiVersion", 2), 100);
        reject(manifest().put("signingCertificateSha256", HASH), 100);
        reject(manifest().put("id", "other"), 100);
        reject(manifest().put("entryClass", "com.untrusted.Entry"), 100);
    }
    @Test public void pinsPublishedDownloadAndBoundsPayload() throws Exception {
        for (String url : new String[]{"http://github.com/game.apk", "https://evil.example/game_kart.apk",
                "https://github.com/Other/Repo/releases/download/game-kart-v1.1.0/game_kart.apk",
                "https://github.com/Elaine-Lynn/GenTV/releases/download/game-kart-v1.1.0/../game_kart.apk"}) {
            JSONObject value = manifest(); value.getJSONObject("plugin").put("url", url); reject(value, 100);
        }
        for (long size : new long[]{0, GameContentManifest.MAX_BYTES + 1}) {
            JSONObject value = manifest(); value.getJSONObject("plugin").put("sizeBytes", size); reject(value, 100);
        }
        JSONObject firstParty = manifest();
        firstParty.getJSONObject("plugin").put("url", "https://example.org/tv/releases/game_kart.apk");
        assertEquals(7, new GameContentManifest(firstParty, listing(), 100, CERT, "https://example.org/tv/").version);
        reject(firstParty, 100);
    }
    @Test public void rejectsTraversalAndDuplicateCatalogEntries() throws Exception {
        for (String id : new String[]{"../kart", "Kart", "kart/other", "", "kart.apk"}) assertFalse(GameListing.validId(id));
        JSONObject catalog = new JSONObject().put("schemaVersion", 1)
            .put("games", new org.json.JSONArray().put(new JSONObject("{id:'kart',title:'Kart',packageName:'com.gentv.game.kart.plugin',entryClass:'com.gentv.game.kart.KartGamePlugin'}")));
        assertEquals(1, GameListing.parse(catalog).size());
        catalog.getJSONArray("games").put(catalog.getJSONArray("games").getJSONObject(0));
        try { GameListing.parse(catalog); fail("Duplicate game"); } catch (java.io.IOException expected) { }
    }
    @Test public void capsMetadataResponses() throws Exception {
        assertArrayEquals(new byte[]{1, 2}, GameNetwork.read(new java.io.ByteArrayInputStream(new byte[]{1, 2}), 2));
        try { GameNetwork.read(new java.io.ByteArrayInputStream(new byte[3]), 2); fail("Oversized response"); }
        catch (java.io.IOException expected) { }
    }
}
