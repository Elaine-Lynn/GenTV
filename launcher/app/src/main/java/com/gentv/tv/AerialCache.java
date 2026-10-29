package com.gentv.tv;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persistent videos. Cache at most five complete films, retaining space for the OS.
 * Never evict playable films just to download more: a full cache plays offline until cleared. */
class AerialCache {
    static final int MAX_VIDEOS = 5;
    static final long LIMIT = Long.MAX_VALUE;
    private static final Object LOCK = new Object();
    private static final Pattern RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
    private final File directory;
    private final long limit;
    private volatile HttpURLConnection connection;

    AerialCache(File directory) { this(directory, LIMIT); }
    AerialCache(File directory, long limit) { this.directory = directory; this.limit = limit; }

    private File file(String url, String suffix) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : hash) name.append(String.format(Locale.ROOT, "%02x", b & 255));
            return new File(directory, name + suffix);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    File cached(String url) {
        try { File f = file(url, ".mov"); return f.isFile() && f.length() > 0 ? f : null; }
        catch (IOException e) { return null; }
    }
    boolean partial(String url) {
        try { return file(url, ".part").isFile(); } catch (IOException e) { return false; }
    }
    int count() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".mov"));
        return files == null ? 0 : files.length;
    }
    private long used() {
        long bytes = 0;
        File[] files = directory.listFiles();
        if (files != null) for (File f : files) bytes += f.length();
        return bytes;
    }
    void cancel() { HttpURLConnection c = connection; if (c != null) c.disconnect(); }
    void clear() throws IOException {
        synchronized (LOCK) {
            File[] files = directory.listFiles();
            if (files != null) for (File f : files)
                if (!f.delete()) throw new IOException("Cannot clear screensaver cache");
        }
    }

    /** Returns null when budget/free space prevents another film. Caller keeps local playback. */
    File download(String url, BooleanSupplier cancelled) throws IOException {
        synchronized (LOCK) {
            if (cancelled.getAsBoolean()) throw new InterruptedIOException();
            File hit = cached(url); if (hit != null) return hit;
            if (count() >= MAX_VIDEOS) return null;
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cache unavailable");
            File part = file(url, ".part"), metadata = file(url, ".etag");
            String saved = metadata.isFile() ? new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8) : "";
            String[] fields = saved.split("\\n", -1);
            String validator = fields[0];
            long expected = -1;
            try { if (fields.length == 2) expected = Long.parseLong(fields[1]); }
            catch (NumberFormatException ignored) { }
            long offset = part.length();
            // A process may stop after writing the last byte but before renaming the file.
            if (offset > 0 && offset == expected && used() <= limit) {
                File result = file(url, ".mov");
                Files.move(part.toPath(), result.toPath(), StandardCopyOption.ATOMIC_MOVE);
                Files.deleteIfExists(metadata.toPath());
                return result;
            }
            // Without a strong validator, never splice potentially different representations.
            if (offset > 0 && !strong(validator)) {
                Files.deleteIfExists(part.toPath()); offset = 0;
            }
            if (used() >= limit || directory.getUsableSpace() < 16L * 1024 * 1024) return null;
            HttpURLConnection c = open(url);
            connection = c;
            c.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) {
                c.setRequestProperty("Range", "bytes=" + offset + "-");
                c.setRequestProperty("If-Range", validator);
            }
            try {
                int status = c.getResponseCode();
                long total;
                if (status == 206 && offset > 0) {
                    Matcher m = RANGE.matcher(String.valueOf(c.getHeaderField("Content-Range")));
                    if (!m.matches()) throw new IOException("Invalid Content-Range");
                    try {
                        total = Long.parseLong(m.group(3));
                        if (Long.parseLong(m.group(1)) != offset || Long.parseLong(m.group(2)) != total - 1
                                || total <= offset || expected > 0 && total != expected) throw new IOException("Unexpected range");
                    } catch (NumberFormatException e) { throw new IOException(e); }
                    String etag = c.getHeaderField("ETag");
                    if (etag != null && !validator.equals(etag)) throw new IOException("Changed representation");
                } else if (status == 200) {
                    offset = 0;
                    Files.deleteIfExists(part.toPath());
                    Files.deleteIfExists(metadata.toPath());
                    total = c.getContentLengthLong();
                } else if (status == 416) {
                    Files.deleteIfExists(part.toPath()); Files.deleteIfExists(metadata.toPath());
                    throw new IOException("Stale partial transfer");
                } else throw new IOException("Video HTTP " + status);
                // Known length lets us reject oversized videos before consuming the body.
                long available = limit - used() + part.length();
                if (total <= 0 || total > available) {
                    Files.deleteIfExists(part.toPath()); Files.deleteIfExists(metadata.toPath());
                    return null;
                }
                if (total - offset > directory.getUsableSpace() - 16L * 1024 * 1024) return null;
                String etag = c.getHeaderField("ETag");
                byte[] meta = strong(etag) ? (etag + "\n" + total).getBytes(StandardCharsets.UTF_8) : new byte[0];
                // Check metadata before writing it, so even partial transfers stay within budget.
                if (used() - part.length() - metadata.length() + total + meta.length > limit) {
                    Files.deleteIfExists(part.toPath()); Files.deleteIfExists(metadata.toPath());
                    return null;
                }
                if (meta.length > 0) Files.write(metadata.toPath(), meta);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, offset > 0)) {
                    byte[] buffer = new byte[64 * 1024]; int n;
                    long received = offset;
                    while (true) {
                        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
                        n = in.read(buffer); if (n == -1) break;
                        if (received + n > total) throw new IOException("Video exceeds declared length");
                        out.write(buffer, 0, n); received += n;
                    }
                    if (received != total) throw new IOException("Incomplete video");
                    out.getFD().sync();
                }
                if (cancelled.getAsBoolean()) throw new InterruptedIOException();
                File result = file(url, ".mov");
                Files.move(part.toPath(), result.toPath(), StandardCopyOption.ATOMIC_MOVE);
                Files.deleteIfExists(metadata.toPath());
                return result;
            } finally { c.disconnect(); connection = null; }
        }
    }
    private static boolean strong(String etag) {
        return etag != null && etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"");
    }
    // Production catalogue already restricts addresses to Apple's HTTPS host. Kept overridable for HTTP fixtures.
    HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        return c;
    }
}
