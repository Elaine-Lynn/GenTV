package com.gentv.tv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class LanWallpaperReceiverTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void servesPageAndRequiresCodeAndOrigin() throws Exception {
        ArrayBlockingQueue<File> received = new ArrayBlockingQueue<>(1);
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", "1234",
                temporary.getRoot(), (file, mime) -> received.offer(file))) {
            HttpURLConnection page = (HttpURLConnection) new URL(server.url()).openConnection();
            assertEquals(200, page.getResponseCode());
            String html = new String(page.getInputStream().readAllBytes(), "UTF-8");
            assertTrue(html.contains("type='file'"));
            assertTrue(html.contains("maxlength='4'"));
            assertTrue(html.contains("[0-9]{4}"));
            assertTrue(html.contains("class='drop'"));
            assertTrue(html.contains("request.upload.onprogress"));
            assertTrue(html.contains("不会自动更换当前壁纸"));
            assertFalse(html.contains("Confirm on your TV"));
            assertTrue(html.contains("图片 ≤ 4 MB"));
            assertTrue(html.contains("视频 ≤ 30 MB"));
            assertTrue(page.getHeaderField("Content-Security-Policy").contains("img-src blob:"));
            page.disconnect();

            byte[] photo = new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2};
            assertEquals(403, upload(server.url(), "0000", null, photo));
            assertEquals(403, uploadWithWrongOrigin(server.url(), photo));
            assertNull(received.poll());
            assertEquals(200, upload(server.url(), "1234", null, photo));
            File staged = received.poll(2, TimeUnit.SECONDS);
            assertNotNull(staged);
            assertArrayEquals(photo, Files.readAllBytes(staged.toPath()));
            staged.delete();
        }
    }

    @Test public void refusesOversizedVideoBeforeWriting() throws Exception {
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", "1234",
                temporary.getRoot(), (file, mime) -> fail("Oversized upload accepted"))) {
            URL url = new URL(server.url());
            try (Socket socket = new Socket(url.getHost(), url.getPort())) {
                String headers = "PUT /upload HTTP/1.1\r\nHost: " + url.getHost() + ":" + url.getPort()
                        + "\r\nX-GenTV-Code: 1234\r\nContent-Type: video/mp4"
                        + "\r\nContent-Length: 31457281\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes("US-ASCII"));
                String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "US-ASCII")).readLine();
                assertTrue(status.startsWith("HTTP/1.1 413 "));
                assertEquals(0, temporary.getRoot().listFiles().length);
            }
        }
    }

    @Test public void acceptsMultipleVideoContainersWhileDialogStaysOpen() throws Exception {
        ArrayBlockingQueue<File> received = new ArrayBlockingQueue<>(2);
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", "1234",
                temporary.getRoot(), (file, mime) -> received.offer(file))) {
            byte[] data = new byte[]{1, 2, 3, 4};
            assertEquals(200, upload(server.url(), "1234", null, data, "video/webm"));
            assertEquals(200, upload(server.url(), "1234", null, data, "video/x-matroska"));
            assertNotNull(received.poll(2, TimeUnit.SECONDS));
            assertNotNull(received.poll(2, TimeUnit.SECONDS));
        }
    }

    @Test public void refusesOversizedImageBeforeWriting() throws Exception {
        try (LanWallpaperReceiver server = new LanWallpaperReceiver("127.0.0.1", "1234",
                temporary.getRoot(), (file, mime) -> fail("Oversized upload accepted"))) {
            URL url = new URL(server.url());
            try (Socket socket = new Socket(url.getHost(), url.getPort())) {
                String headers = "PUT /upload HTTP/1.1\r\nHost: " + url.getHost() + ":" + url.getPort()
                        + "\r\nX-GenTV-Code: 1234\r\nContent-Type: image/png"
                        + "\r\nContent-Length: 4194305\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes("US-ASCII"));
                String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "US-ASCII")).readLine();
                assertTrue(status.startsWith("HTTP/1.1 413 "));
                assertEquals(0, temporary.getRoot().listFiles().length);
            }
        }
    }

    private static int upload(String base, String code, String origin, byte[] body) throws Exception {
        return upload(base, code, origin, body, "image/jpeg");
    }

    private static int upload(String base, String code, String origin, byte[] body, String mime) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "upload").openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        connection.setRequestProperty("Content-Type", mime);
        connection.setRequestProperty("X-GenTV-Code", code);
        if (origin != null) connection.setRequestProperty("Origin", origin);
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            return connection.getResponseCode();
        } finally { connection.disconnect(); }
    }

    private static int uploadWithWrongOrigin(String base, byte[] body) throws Exception {
        URL url = new URL(base);
        try (Socket socket = new Socket(url.getHost(), url.getPort())) {
            String headers = "PUT /upload HTTP/1.1\r\nHost: " + url.getHost() + ":" + url.getPort()
                    + "\r\nOrigin: http://wrong.example\r\nX-GenTV-Code: 1234"
                    + "\r\nContent-Type: image/jpeg\r\nContent-Length: " + body.length + "\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes("US-ASCII"));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "US-ASCII")).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }
}
