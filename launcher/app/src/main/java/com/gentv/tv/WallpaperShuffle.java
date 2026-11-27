package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * The wallpaper gallery's Shuffle choices as a lasting mode: the chosen scope's online wallpapers play one after
 * another in a shuffled order, a picture for 30 minutes and a video for a day. Every round is a fresh shuffle, so
 * nothing repeats within a round. Only wallpapers already on the TV are switched to, so a change is instant and
 * works offline; while Home is on screen the next ones download quietly, and at most {@link #KEEP} of those
 * downloads are kept. Choosing any other wallpaper ends the mode.
 */
final class WallpaperShuffle {
    static final long IMAGE_INTERVAL_MS = 30L * 60 * 1000;
    static final long VIDEO_INTERVAL_MS = 24L * 60 * 60 * 1000;
    /** Shuffle's own downloads kept on the TV; older ones go, least recently shown first. */
    static final int KEEP = 8;
    /** Upcoming wallpapers kept ready on the TV ahead of the current one. */
    static final int READY_AHEAD = 2;
    private static final String TAG = "GenTV.Shuffle";
    private static final String PREFS = "wallpaper_shuffle";
    private static final ExecutorService PREFETCH = Executors.newSingleThreadExecutor();
    private static volatile boolean prefetching;
    private static final Random RANDOM = new Random();

    /** One online wallpaper of the shuffled scope. */
    static final class Item {
        final String id, kind, path, title;
        Item(String id, String kind, String path, String title) {
            this.id = id; this.kind = kind; this.path = path; this.title = title;
        }
        static Item of(JSONObject json) {
            return new Item(json.optString("id"), json.optString("kind"), json.optString("path"),
                    json.optString("title"));
        }
        boolean video() { return "video".equals(kind); }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("kind", kind).put("path", path).put("title", title);
        }
    }

    private WallpaperShuffle() { }

    // ---- Pure rules, unit tested ----

    static long interval(boolean video) { return video ? VIDEO_INTERVAL_MS : IMAGE_INTERVAL_MS; }

    static boolean due(boolean video, long switchedAt, long now) {
        return switchedAt <= 0 || now < switchedAt || now - switchedAt >= interval(video);
    }

    /** A new round: every ID once, in random order, not starting with {@code avoidFirst} when there is a choice. */
    static List<String> shuffled(List<String> ids, Random random, String avoidFirst) {
        List<String> order = new ArrayList<>(ids);
        Collections.shuffle(order, random);
        if (order.size() > 1 && order.get(0).equals(avoidFirst)) {
            Collections.swap(order, 0, 1 + random.nextInt(order.size() - 1));
        }
        return order;
    }

    /** The first position after {@code from} in this round whose wallpaper is on the TV, or -1. */
    static int nextReady(List<String> queue, int from, Predicate<String> ready) {
        for (int i = Math.max(-1, from) + 1; i < queue.size(); i++) if (ready.test(queue.get(i))) return i;
        return -1;
    }

    /** How many wallpapers after {@code from} in this round are already on the TV. */
    static int readyAhead(List<String> queue, int from, Predicate<String> ready) {
        int count = 0;
        for (int i = Math.max(-1, from) + 1; i < queue.size(); i++) if (ready.test(queue.get(i))) count++;
        return count;
    }

    /**
     * Shuffle's own downloads to delete so at most {@code keep} remain: least recently shown (or downloaded)
     * first, never one in {@code protect}.
     */
    static List<String> evictions(Map<String, Long> lastUsed, Set<String> owned, Set<String> protect, int keep) {
        List<String> candidates = new ArrayList<>();
        for (String id : owned) if (!protect.contains(id)) candidates.add(id);
        int excess = owned.size() - keep;
        if (excess <= 0) return new ArrayList<>();
        Collections.sort(candidates, (a, b) -> Long.compare(value(lastUsed, a), value(lastUsed, b)));
        return new ArrayList<>(candidates.subList(0, Math.min(excess, candidates.size())));
    }

    private static long value(Map<String, Long> map, String key) {
        Long value = map.get(key);
        return value == null ? 0L : value;
    }

    // ---- State ----

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The scope being shuffled ("all", "pets", …), or "" when Shuffle is off. */
    static String scope(Context context) { return prefs(context).getString("scope", ""); }

    static boolean active(Context context) { return !scope(context).isEmpty(); }

    /** Ends the mode; the wallpapers it downloaded stay on the TV and in its cache limit. */
    static synchronized void stop(Context context) {
        prefs(context).edit().remove("scope").remove("queue").remove("index").remove("current")
                .remove("currentKind").remove("switchedAt").apply();
    }

    /** Starts Shuffle over a scope's wallpapers, avoiding the one on screen as the first. */
    static synchronized void start(Context context, String scope, List<JSONObject> pool, String avoidItemId) {
        List<Item> items = items(pool);
        saveQueue(prefs(context).edit().putString("scope", scope).remove("current").remove("switchedAt")
                .putInt("index", -1), order(items, avoidItemId)).apply();
    }

    /** The first wallpaper of the round that is already on the TV, or else the first of the round. */
    static synchronized Item first(Context context) {
        List<Item> queue = queue(context);
        if (queue.isEmpty()) return null;
        int ready = nextReady(ids(queue), -1, readyTest(context));
        return queue.get(ready >= 0 ? ready : 0);
    }

    /** Records the wallpaper Shuffle just put on screen as its local ID. */
    static synchronized void showing(Context context, Item item, String localId) {
        List<Item> queue = queue(context);
        int index = ids(queue).indexOf(item.id);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor edit = prefs(context).edit().putInt("index", index).putString("current", localId)
                .putString("currentKind", item.kind).putLong("switchedAt", now);
        Map<String, Long> used = lastUsed(context);
        used.put(localId, now);
        edit.putString("used", new JSONObject(used).toString()).apply();
    }

    /** The next wallpaper of the round that is not on the TV yet (to download now), or null. */
    static synchronized Item nextToDownload(Context context) {
        List<Item> queue = queue(context);
        int index = prefs(context).getInt("index", -1);
        for (int step = 1; step <= queue.size(); step++) {
            Item candidate = queue.get(Math.floorMod(index + step, queue.size()));
            if (WallpaperDownloads.local(context, candidate.id, candidate.path) == null) return candidate;
        }
        return null;
    }

    /** A wallpaper the gallery downloaded for Shuffle counts towards its cache limit. */
    static synchronized void adopt(Context context, String localId) {
        SharedPreferences prefs = prefs(context);
        Set<String> owned = new HashSet<>(prefs.getStringSet("owned", new HashSet<>()));
        owned.add(localId);
        prefs.edit().putStringSet("owned", owned).apply();
    }

    /**
     * Home: when the current wallpaper's time is up, moves to the next one already on the TV and returns its
     * local ID for Home to show; null when nothing changes. A wallpaper chosen elsewhere ends the mode.
     */
    static synchronized String rotateIfDue(Context context, long now) {
        if (!active(context)) return null;
        SharedPreferences prefs = prefs(context);
        String current = prefs.getString("current", "");
        if (current.isEmpty() || !current.equals(BundledWallpapers.selected(context))) {
            stop(context);
            return null;
        }
        boolean video = "video".equals(prefs.getString("currentKind", ""));
        if (!due(video, prefs.getLong("switchedAt", 0), now)) return null;
        return advance(context);
    }

    /** The gallery's Shuffle card chosen again: the next wallpaper now. Null when no other one is on the TV. */
    static synchronized String skip(Context context) { return active(context) ? advance(context) : null; }

    private static String advance(Context context) {
        SharedPreferences prefs = prefs(context);
        String current = prefs.getString("current", "");
        List<Item> queue = queue(context);
        if (queue.isEmpty()) return null;
        Predicate<String> ready = readyOther(context, queue, current);
        int index = prefs.getInt("index", -1);
        int next = nextReady(ids(queue), index, ready);
        if (next < 0) {
            // The rest of this round is not on the TV (yet): start the next round among what is, so Shuffle keeps
            // changing offline; the downloads fill in the newer order.
            Item shown = index >= 0 && index < queue.size() ? queue.get(index) : null;
            queue = order(queue, shown == null ? "" : shown.id);
            saveQueue(prefs.edit().putInt("index", -1), queue).apply();
            next = nextReady(ids(queue), -1, readyOther(context, queue, current));
            if (next < 0) return null;
        }
        Item item = queue.get(next);
        String localId = WallpaperDownloads.local(context, item.id, item.path);
        if (localId == null) return null;
        showing(context, item, localId);
        return localId;
    }

    /** The gallery's catalogue changed: drop wallpapers that left the scope, add new ones at the end. */
    static synchronized void updatePool(Context context, String scope, List<JSONObject> pool) {
        if (!scope.equals(scope(context))) return;
        List<Item> queue = queue(context);
        List<Item> latest = items(pool);
        Set<String> latestIds = new HashSet<>(ids(latest));
        Set<String> known = new HashSet<>(ids(queue));
        String shownId = currentItemId(context, queue);
        List<Item> kept = new ArrayList<>();
        for (Item item : queue) if (latestIds.contains(item.id)) kept.add(item);
        List<Item> added = new ArrayList<>();
        for (Item item : latest) if (!known.contains(item.id)) added.add(item);
        Collections.shuffle(added, RANDOM);
        kept.addAll(added);
        if (kept.size() == queue.size() && added.isEmpty()) return;
        int index = shownId == null ? -1 : ids(kept).indexOf(shownId);
        saveQueue(prefs(context).edit().putInt("index", index), kept).apply();
    }

    // ---- Downloads ----

    /**
     * While Home is on screen: downloads the next wallpaper of the round that is not on the TV yet, when fewer
     * than {@link #READY_AHEAD} upcoming ones are ready and the network is not metered; then trims Shuffle's
     * downloads to {@link #KEEP}. One at a time, in the background.
     */
    static void prefetch(Context context) {
        Context app = context.getApplicationContext();
        if (prefetching || !active(app) || !unmetered(app)) return;
        prefetching = true;
        PREFETCH.execute(() -> {
            try { prefetchNow(app); }
            catch (Exception | OutOfMemoryError failure) { Log.w(TAG, "Prefetch failed", failure); }
            finally { prefetching = false; }
        });
    }

    private static void prefetchNow(Context context) throws Exception {
        Item target;
        synchronized (WallpaperShuffle.class) {
            if (!active(context)) return;
            List<Item> queue = queue(context);
            int index = prefs(context).getInt("index", -1);
            Predicate<String> ready = readyTest(context, queue);
            if (readyAhead(ids(queue), index, ready) >= READY_AHEAD) return;
            target = null;
            for (int step = 1; step <= queue.size() && target == null; step++) {
                Item candidate = queue.get(Math.floorMod(index + step, queue.size()));
                if (WallpaperDownloads.local(context, candidate.id, candidate.path) == null) target = candidate;
            }
            if (target == null) return;
        }
        HttpURLConnection connection = TvCloud.open(WallpaperGalleryActivity.assetUrl(target.path));
        String localId;
        try {
            localId = UserWallpaper.importDownload(context, connection.getInputStream(), target.video());
        } finally {
            connection.disconnect();
        }
        WallpaperDownloads.remember(context, target.id, target.path, localId);
        Log.i(TAG, "Ready: " + target.id);
        synchronized (WallpaperShuffle.class) {
            SharedPreferences prefs = prefs(context);
            Set<String> owned = new HashSet<>(prefs.getStringSet("owned", new HashSet<>()));
            owned.add(localId);
            Map<String, Long> used = lastUsed(context);
            if (!used.containsKey(localId)) used.put(localId, System.currentTimeMillis());
            Set<String> protect = new HashSet<>();
            protect.add(localId);
            protect.add(prefs.getString("current", ""));
            protect.add(BundledWallpapers.selected(context));
            for (String old : evictions(used, owned, protect, KEEP)) {
                UserWallpaper.delete(context, old);
                owned.remove(old);
                used.remove(old);
            }
            prefs.edit().putStringSet("owned", owned).putString("used", new JSONObject(used).toString()).apply();
        }
    }

    private static boolean unmetered(Context context) {
        ConnectivityManager connectivity = CompatApi.service(context, ConnectivityManager.class);
        if (connectivity == null) return false;
        NetworkInfo network = connectivity.getActiveNetworkInfo();
        return network != null && network.isConnected() && !connectivity.isActiveNetworkMetered();
    }

    // ---- Storage helpers ----

    private static List<Item> items(List<JSONObject> pool) {
        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JSONObject json : pool) {
            Item item = Item.of(json);
            if (!item.id.isEmpty() && !item.path.isEmpty() && seen.add(item.id)) items.add(item);
        }
        return items;
    }

    private static List<Item> order(List<Item> items, String avoidFirst) {
        Map<String, Item> byId = new HashMap<>();
        for (Item item : items) byId.put(item.id, item);
        List<Item> ordered = new ArrayList<>();
        for (String id : shuffled(ids(items), RANDOM, avoidFirst)) ordered.add(byId.get(id));
        return ordered;
    }

    private static List<String> ids(List<Item> items) {
        List<String> ids = new ArrayList<>();
        for (Item item : items) ids.add(item.id);
        return ids;
    }

    private static List<Item> queue(Context context) {
        List<Item> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("queue", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null) items.add(Item.of(json));
            }
        } catch (JSONException ignored) { }
        return items;
    }

    private static SharedPreferences.Editor saveQueue(SharedPreferences.Editor edit, List<Item> queue) {
        JSONArray array = new JSONArray();
        for (Item item : queue) {
            try { array.put(item.json()); } catch (JSONException ignored) { }
        }
        return edit.putString("queue", array.toString());
    }

    private static String currentItemId(Context context, List<Item> queue) {
        int index = prefs(context).getInt("index", -1);
        return index >= 0 && index < queue.size() ? queue.get(index).id : null;
    }

    private static Map<String, Long> lastUsed(Context context) {
        Map<String, Long> used = new HashMap<>();
        try {
            JSONObject json = new JSONObject(prefs(context).getString("used", "{}"));
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                used.put(key, json.optLong(key));
            }
        } catch (JSONException ignored) { }
        return used;
    }

    private static Predicate<String> readyTest(Context context) { return readyTest(context, queue(context)); }

    private static Predicate<String> readyTest(Context context, List<Item> queue) {
        Map<String, Item> byId = new HashMap<>();
        for (Item item : queue) byId.put(item.id, item);
        return id -> {
            Item item = byId.get(id);
            return item != null && WallpaperDownloads.local(context, item.id, item.path) != null;
        };
    }

    /** On the TV and not the wallpaper already on screen. */
    private static Predicate<String> readyOther(Context context, List<Item> queue, String currentLocal) {
        Map<String, Item> byId = new HashMap<>();
        for (Item item : queue) byId.put(item.id, item);
        return id -> {
            Item item = byId.get(id);
            if (item == null) return false;
            String local = WallpaperDownloads.local(context, item.id, item.path);
            return local != null && !local.equals(currentLocal);
        };
    }
}
