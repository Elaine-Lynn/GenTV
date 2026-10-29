package com.gentv.tv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Worker-owned persistent traffic limits; wall-clock rollback never grants extra quota. */
final class WallpaperPolicy {
    static final long DAY = 24L * 60 * 60 * 1000;
    static final long REFRESH = 6L * 60 * 60 * 1000;
    static final long DAILY_BYTES = 16L * 1024 * 1024;
    static final int MAX_IMAGE = 8 * 1024 * 1024;
    private final Properties state = new Properties();
    WallpaperPolicy(byte[] saved) throws IOException {
        if (saved != null) state.load(new ByteArrayInputStream(saved));
    }
    byte[] bytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); state.store(out, null); return out.toByteArray();
    }
    private long get(String key, long fallback) {
        try { return Long.parseLong(state.getProperty(key, "")); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private void put(String key, long value) { state.setProperty(key, Long.toString(value)); }
    boolean manifestDue(long now, long interval) {
        long last = get("check", -1), delay = get("checkDelay", interval);
        return last < 0 || now >= last && now - last >= delay;
    }
    void seedManifest(long fetchedAt, long interval) {
        if (!state.containsKey("check") && fetchedAt > 0) manifestSuccess(fetchedAt, interval);
    }
    void manifestSuccess(long now, long interval) { put("check", now); put("checkDelay", interval); put("checkFailures", 0); }
    void manifestFailure(long now) {
        long count = Math.min(4, get("checkFailures", 0) + 1);
        put("check", now); put("checkFailures", count);
        put("checkDelay", Math.min(REFRESH, (1L << (count - 1)) * 60 * 60 * 1000));
    }
    private String key(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : digest) text.append(String.format(Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    boolean mayDownload(String url, long now) {
        String key = key(url);
        if (state.containsKey("seen." + key)) return false;
        long failed = get("failed." + key, -1);
        long count = Math.min(6, get("failures." + key, 1));
        return failed < 0 || now >= failed && now - failed >= Math.min(DAY, (1L << (count - 1)) * 60 * 60 * 1000);
    }
    void failed(String url, long now) {
        String key = key(url); put("failed." + key, now);
        put("failures." + key, Math.min(6, get("failures." + key, 0) + 1));
    }
    void downloaded(String url, long now) {
        String key = key(url); put("seen." + key, now);
        state.remove("failed." + key); state.remove("failures." + key);
    }
    boolean mayReplace(long now) {
        long last = get("replace", -1); return last < 0 || now >= last && now - last >= DAY;
    }
    void replaced(long now) { put("replace", now); }
    // Reserve the worst case before a request. A process death conservatively consumes that reservation.
    boolean reserve(long now) {
        long start = get("budgetStart", -1);
        if (start < 0 || now >= start && now - start >= DAY) {
            put("budgetStart", now); put("spent", 0);
        }
        long spent = get("spent", DAILY_BYTES);
        if (spent > DAILY_BYTES - MAX_IMAGE) return false;
        put("spent", spent + MAX_IMAGE); return true;
    }
    void finish(int received) {
        put("spent", Math.max(0, get("spent", DAILY_BYTES) - MAX_IMAGE + Math.max(0, Math.min(MAX_IMAGE, received))));
    }
    // Bound URL history to this source's current catalogue plus locally retained images.
    void retain(Collection<String> urls) {
        Set<String> keys = new HashSet<>(); for (String url : urls) keys.add(key(url));
        for (String name : new HashSet<>(state.stringPropertyNames())) {
            int dot = name.indexOf('.');
            if (dot >= 0 && (name.startsWith("seen.") || name.startsWith("failed.") || name.startsWith("failures."))
                    && !keys.contains(name.substring(dot + 1))) state.remove(name);
        }
    }
}
