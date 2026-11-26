package com.gentv.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WallpaperCatalogSnapshotTest {
    private static final String BASE = "https://example.test/tv/";

    private JSONObject response() throws Exception {
        JSONObject item = new JSONObject().put("id", "kitten").put("title", "午后小猫")
                .put("kind", "video").put("path", "wallpapers/kitten.mp4")
                .put("preview", "wallpapers/kitten.jpg");
        return new JSONObject().put("categories", new JSONArray().put(new JSONObject()
                .put("id", "pets").put("items", new JSONArray().put(item))));
    }

    @Test public void successfulCatalogSurvivesSerializationWithVideoAndPreviewMetadata() throws Exception {
        String saved = WallpaperCatalogSnapshot.encode(BASE, response(), 1234);
        JSONObject item = WallpaperCatalogSnapshot.decode(BASE, saved).getJSONObject(0)
                .getJSONArray("items").getJSONObject(0);
        assertEquals("午后小猫", item.getString("title"));
        assertEquals("wallpapers/kitten.mp4", item.getString("path"));
        assertEquals("wallpapers/kitten.jpg", item.getString("preview"));
        assertEquals(1234, new JSONObject(saved).getLong("fetchedAt"));
    }

    @Test public void errorResponseCannotBecomeASuccessfulCache() throws Exception {
        try {
            WallpaperCatalogSnapshot.encode(BASE, new JSONObject().put("error", "Unavailable"), 1234);
            fail("Error response was accepted");
        } catch (org.json.JSONException expected) { }
    }

    @Test public void cacheFromAnotherServerOrDisabledServiceIsRejected() throws Exception {
        String saved = WallpaperCatalogSnapshot.encode(BASE, response(), 1234);
        for (String endpoint : new String[]{"https://other.test/tv/", ""}) {
            try { WallpaperCatalogSnapshot.decode(endpoint, saved); fail("Cache source mismatch accepted"); }
            catch (java.io.IOException expected) { }
        }
    }

    @Test public void brokenCacheAndMissingItemFieldsAreRejected() throws Exception {
        try { WallpaperCatalogSnapshot.decode(BASE, "{broken"); fail("Broken cache accepted"); }
        catch (org.json.JSONException expected) { }
        JSONObject malformed = response();
        malformed.getJSONArray("categories").getJSONObject(0).getJSONArray("items")
                .getJSONObject(0).remove("kind");
        try { WallpaperCatalogSnapshot.encode(BASE, malformed, 1234); fail("Invalid item accepted"); }
        catch (org.json.JSONException expected) { }
    }

    @Test public void validEmptyCatalogReplacesPreviouslyPopulatedContent() throws Exception {
        JSONObject empty = new JSONObject().put("categories", new JSONArray());
        assertEquals(0, WallpaperCatalogSnapshot.decode(BASE,
                WallpaperCatalogSnapshot.encode(BASE, empty, 1234)).length());
    }

    @Test public void oversizedSnapshotIsRejected() throws Exception {
        JSONObject large = response().put("padding", new String(new char[WallpaperCatalogSnapshot.MAX_BYTES]));
        try { WallpaperCatalogSnapshot.encode(BASE, large, 1234); fail("Oversized cache accepted"); }
        catch (java.io.IOException expected) { }
    }
}
