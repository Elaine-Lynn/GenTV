package com.gentv.tv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class LanWallpaperReceiverTest {
    private static final String TOKEN = "k7q2";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void servesTheMinimalPageOnlyAtItsKeyedAddress() throws Exception {
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", TOKEN,
                temporary.getRoot(), (file, mime) -> { })) {
            assertTrue(server.url().endsWith("/" + TOKEN));
            HttpURLConnection page = (HttpURLConnection) new URL(server.url()).openConnection();
            assertEquals(200, page.getResponseCode());
            String html = new String(page.getInputStream().readAllBytes(), "UTF-8");
            assertTrue(html.contains("type='file'"));
            assertTrue(html.contains("class='card'"));
            assertTrue(html.contains("r.upload.onprogress"));
            assertTrue(html.contains("/upload"));
            // No code to read off the TV or type in.
            assertFalse(html.contains("X-GenTV-Code"));
            assertFalse(html.contains("验证码"));
            assertTrue(page.getHeaderField("Content-Security-Policy").contains("img-src blob:"));
            assertTrue(page.getHeaderField("Content-Security-Policy").contains("media-src blob:"));
            page.disconnect();

            URL base = new URL(server.url());
            assertEquals(404, status(new URL(base, "/")));
            assertEquals(404, status(new URL(base, "/abcd")));
        }
    }

    @Test public void acceptsUploadsOnlyUnderTheKeyAndFromItsOwnOrigin() throws Exception {
        ArrayBlockingQueue<File> received = new ArrayBlockingQueue<>(1);
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", TOKEN,
                temporary.getRoot(), (file, mime) -> received.offer(file))) {
            byte[] photo = new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2};
            String root = new URL(new URL(server.url()), "/").toString();
            assertEquals(404, upload(root + "upload", photo, "image/jpeg"));
            assertEquals(404, upload(root + "zzzz/upload", photo, "image/jpeg"));
            assertEquals(403, uploadWithWrongOrigin(server.url(), photo));
            assertNull(received.poll());
            assertEquals(200, upload(server.url() + "/upload", photo, "image/jpeg"));
            File staged = received.poll(2, TimeUnit.SECONDS);
            assertNotNull(staged);
            assertArrayEquals(photo, Files.readAllBytes(staged.toPath()));
            staged.delete();
        }
    }

    @Test public void stopsAfterRepeatedGuesses() throws Exception {
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", TOKEN,
                temporary.getRoot(), (file, mime) -> fail("Guess accepted"))) {
            URL base = new URL(server.url());
            // The browser's own icon request never counts.
            for (int i = 0; i < 20; i++) assertEquals(404, status(new URL(base, "/favicon.ico")));
            for (int i = 0; i < 12; i++) assertEquals(404, status(new URL(base, "/guess" + i)));
            try {
                status(new URL(server.url()));
                fail("Receiver still open");
            } catch (java.io.IOException expected) {
                // closed
            }
        }
    }

    @Test public void tokensAvoidLookAlikeCharacters() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 200; i++) assertTrue(LanWallpaperReceiver.newToken(random).matches("[a-hjkmnp-z2-9]{4}"));
    }

    @Test public void acceptsMultipleVideoContainersWhileTheSheetStaysOpen() throws Exception {
        ArrayBlockingQueue<File> received = new ArrayBlockingQueue<>(2);
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", TOKEN,
                temporary.getRoot(), (file, mime) -> received.offer(file))) {
            byte[] data = new byte[]{1, 2, 3, 4};
            assertEquals(200, upload(server.url() + "/upload", data, "video/webm"));
            assertEquals(200, upload(server.url() + "/upload", data, "video/x-matroska"));
            assertNotNull(received.poll(2, TimeUnit.SECONDS));
            assertNotNull(received.poll(2, TimeUnit.SECONDS));
        }
    }

    @Test public void refusesOversizedVideoBeforeWriting() throws Exception {
        assertRefusedBeforeWriting("video/mp4", 31457281);
    }

    @Test public void refusesOversizedImageBeforeWriting() throws Exception {
        assertRefusedBeforeWriting("image/png", 4194305);
    }

    private void assertRefusedBeforeWriting(String mime, long length) throws Exception {
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", TOKEN,
                temporary.getRoot(), (file, type) -> fail("Oversized upload accepted"))) {
            URL url = new URL(server.url());
            try (Socket socket = new Socket(url.getHost(), url.getPort())) {
                String headers = "PUT /" + TOKEN + "/upload HTTP/1.1\r\nHost: " + url.getHost() + ":" + url.getPort()
                        + "\r\nContent-Type: " + mime + "\r\nContent-Length: " + length + "\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes("US-ASCII"));
                String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "US-ASCII")).readLine();
                assertTrue(status.startsWith("HTTP/1.1 413 "));
                assertEquals(0, temporary.getRoot().listFiles().length);
            }
        }
    }

    private static int status(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try { return connection.getResponseCode(); } finally { connection.disconnect(); }
    }

    private static int upload(String url, byte[] body, String mime) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        connection.setRequestProperty("Content-Type", mime);
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            return connection.getResponseCode();
        } finally { connection.disconnect(); }
    }

    private static int uploadWithWrongOrigin(String page, byte[] body) throws Exception {
        URL url = new URL(page);
        try (Socket socket = new Socket(url.getHost(), url.getPort())) {
            String headers = "PUT /" + TOKEN + "/upload HTTP/1.1\r\nHost: " + url.getHost() + ":" + url.getPort()
                    + "\r\nOrigin: http://wrong.example"
                    + "\r\nContent-Type: image/jpeg\r\nContent-Length: " + body.length + "\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes("US-ASCII"));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "US-ASCII")).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }
}
