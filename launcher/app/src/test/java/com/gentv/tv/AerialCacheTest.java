package com.gentv.tv;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class AerialCacheTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String URL = "https://example.test/video.mov";
    private static final byte[] VIDEO = new byte[200_000];
    static { new Random(42).nextBytes(VIDEO); }

    @Test public void sixthFilmDoesNotDownloadOrEvictCachedFilms() throws Exception {
        File dir = temp.newFolder();
        Fixture cache = new Fixture(dir, Long.MAX_VALUE);
        for (int i = 0; i < 5; i++) assertNotNull(cache.download(URL + "?id=" + i, () -> false));
        assertEquals(5, cache.count());
        assertNull(cache.download(URL + "?id=5", () -> false));
        assertEquals(5, cache.requests);
        for (int i = 0; i < 5; i++) assertNotNull(cache.cached(URL + "?id=" + i));
    }

    private static class Fixture extends AerialCache {
        int requests, transferred;
        boolean ignoreRange, wrongRange;
        String etag = "\"v1\"";
        AtomicBoolean interrupt;
        int interruptAt = 1;
        Map<String, String> headers;
        Fixture(File dir, long limit) { super(dir, limit); }
        @Override HttpURLConnection open(String url) throws IOException {
            requests++;
            headers = new HashMap<>();
            return new HttpURLConnection(new URL(url)) {
                public void disconnect() { }
                public boolean usingProxy() { return false; }
                public void connect() { }
                @Override public void setRequestProperty(String k, String v) { headers.put(k, v); }
                private int offset() {
                    String range = headers.get("Range");
                    return range == null || ignoreRange || !etag.equals(headers.get("If-Range")) ? 0
                            : Integer.parseInt(range.substring(6, range.length() - 1));
                }
                @Override public int getResponseCode() { return offset() > 0 ? 206 : 200; }
                @Override public long getContentLengthLong() { return VIDEO.length - offset(); }
                @Override public String getHeaderField(String name) {
                    if (name.equals("ETag")) return etag;
                    if (name.equals("Content-Range")) return "bytes " + (wrongRange ? 0 : offset()) + "-" + (VIDEO.length - 1) + "/" + VIDEO.length;
                    return null;
                }
                @Override public InputStream getInputStream() {
                    return new ByteArrayInputStream(VIDEO, offset(), VIDEO.length - offset()) {
                        @Override public synchronized int read(byte[] b, int off, int len) {
                            int n = super.read(b, off, Math.min(len, 32000));
                            if (n > 0) { transferred += n; if (interrupt != null && transferred >= interruptAt) interrupt.set(true); }
                            return n;
                        }
                    };
                }
            };
        }
    }
    @Test public void completeCacheSurvivesRestartAndDoesNotRequestAgain() throws Exception {
        File dir = temp.newFolder(); Fixture cache = new Fixture(dir, 500_000);
        assertArrayEquals(VIDEO, Files.readAllBytes(cache.download(URL, () -> false).toPath()));
        Fixture restarted = new Fixture(dir, 500_000);
        assertNotNull(restarted.download(URL, () -> false));
        assertEquals(0, restarted.requests);
    }
    private Fixture interrupted(File dir) throws Exception {
        Fixture cache = new Fixture(dir, 500_000);
        AtomicBoolean stopped = new AtomicBoolean(); cache.interrupt = stopped;
        try { cache.download(URL, stopped::get); fail(); } catch (InterruptedIOException expected) { }
        assertTrue(cache.partial(URL)); assertNull(cache.cached(URL));
        assertEquals(32000, cache.transferred);
        return cache;
    }
    @Test public void interruptedTransferResumesAfterRestartWithoutDuplicateBytes() throws Exception {
        File dir = temp.newFolder(); interrupted(dir);
        Fixture next = new Fixture(dir, 500_000);
        assertArrayEquals(VIDEO, Files.readAllBytes(next.download(URL, () -> false).toPath()));
        assertEquals("bytes=32000-", next.headers.get("Range"));
        assertEquals("\"v1\"", next.headers.get("If-Range"));
        assertEquals(VIDEO.length - 32000, next.transferred);
    }
    @Test public void serverIgnoringRangeRestartsRatherThanAppending() throws Exception {
        File dir = temp.newFolder(); interrupted(dir);
        Fixture next = new Fixture(dir, 500_000); next.ignoreRange = true;
        assertArrayEquals(VIDEO, Files.readAllBytes(next.download(URL, () -> false).toPath()));
    }
    @Test public void changedEtagRestartsRatherThanMixingVersions() throws Exception {
        File dir = temp.newFolder(); interrupted(dir);
        Fixture next = new Fixture(dir, 500_000); next.etag = "\"v2\"";
        assertArrayEquals(VIDEO, Files.readAllBytes(next.download(URL, () -> false).toPath()));
    }
    @Test public void invalidRangeCannotCorruptPartialFile() throws Exception {
        File dir = temp.newFolder(); interrupted(dir);
        Fixture next = new Fixture(dir, 500_000); next.wrongRange = true;
        try { next.download(URL, () -> false); fail(); } catch (IOException expected) { }
        assertEquals(0, next.transferred); assertNull(next.cached(URL));
    }
    @Test public void capacityRejectsBodyAndPreservesExistingVideo() throws Exception {
        Fixture cache = new Fixture(temp.newFolder(), 300_000);
        assertNotNull(cache.download(URL, () -> false));
        int bytes = cache.transferred;
        assertNull(cache.download(URL + "?second", () -> false));
        assertEquals(bytes, cache.transferred); assertNotNull(cache.cached(URL));
    }
    @Test public void interruptionAfterLastBytePromotesWithoutAnotherRequest() throws Exception {
        File dir = temp.newFolder(); Fixture cache = new Fixture(dir, 500_000);
        AtomicBoolean stopped = new AtomicBoolean(); cache.interrupt = stopped;
        cache.interruptAt = VIDEO.length;
        try { cache.download(URL, stopped::get); fail(); } catch (InterruptedIOException expected) { }
        Fixture next = new Fixture(dir, 500_000);
        assertArrayEquals(VIDEO, Files.readAllBytes(next.download(URL, () -> false).toPath()));
        assertEquals(0, next.requests);
    }
    @Test public void validatorMetadataCannotPushCacheAboveLimit() throws Exception {
        File dir = temp.newFolder(); Fixture cache = new Fixture(dir, VIDEO.length);
        assertNull(cache.download(URL, () -> false));
        assertEquals(0, cache.transferred);
        long bytes = 0; for (File file : Objects.requireNonNull(dir.listFiles())) bytes += file.length();
        assertTrue(bytes <= VIDEO.length);
    }
    @Test public void clearDeletesCompleteAndPartialThenAllowsNewDownload() throws Exception {
        File dir = temp.newFolder(); Fixture cache = new Fixture(dir, 500_000);
        cache.download(URL, () -> false);
        AtomicBoolean stopped = new AtomicBoolean(); cache.interrupt = stopped;
        try { cache.download(URL + "?second", stopped::get); fail(); } catch (InterruptedIOException expected) { }
        cache.clear(); assertEquals(0, Objects.requireNonNull(dir.list()).length);
        cache.interrupt = null;
        assertNotNull(cache.download(URL, () -> false)); assertEquals(3, cache.requests);
    }
}
