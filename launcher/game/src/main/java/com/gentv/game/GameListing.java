package com.gentv.game;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Small catalog contract; game-specific code and controls are never bundled into this list. */
final class GameListing {
    final String id, title, titleEn, description, descriptionEn, packageName, entryClass;

    GameListing(JSONObject item) throws Exception {
        id = item.getString("id");
        if (!validId(id)) throw new IOException("Invalid game ID");
        packageName = item.getString("packageName");
        entryClass = item.getString("entryClass");
        String prefix = "com.gentv.game." + id + ".";
        if (!packageName.equals(prefix + "plugin") || !entryClass.startsWith(prefix)
                || !entryClass.matches("[A-Za-z0-9_.$]+")) throw new IOException("Invalid game entry");
        title = text(item.getString("title"), 80);
        titleEn = text(item.optString("titleEn", title), 80);
        description = text(item.optString("description", ""), 240);
        descriptionEn = text(item.optString("descriptionEn", description), 240);
    }

    static boolean validId(String id) { return id != null && id.matches("[a-z][a-z0-9_]{0,39}"); }
    private static String text(String text, int max) throws IOException {
        if (text.length() > max) throw new IOException("Catalog text too long");
        return text;
    }
    static List<GameListing> parse(JSONObject catalog) throws Exception {
        if (catalog.getInt("schemaVersion") != 1) throw new IOException("Unsupported game catalog");
        JSONArray items = catalog.getJSONArray("games");
        if (items.length() > 100) throw new IOException("Too many games");
        List<GameListing> result = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            GameListing game = new GameListing(items.getJSONObject(i));
            if (!ids.add(game.id)) throw new IOException("Duplicate game ID");
            result.add(game);
        }
        return result;
    }
}
