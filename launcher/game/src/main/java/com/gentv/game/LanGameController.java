package com.gentv.game;

import android.content.Context;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;

/** Short-lived, LAN-only controller; active only while the game activity is visible. */
final class LanGameController implements Closeable {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset ASCII = Charset.forName("US-ASCII");
    interface Listener {
        boolean ready();
        void connected();
        void key(String key, boolean down);
    }

    private final ServerSocket server;
    private final Thread worker;
    private final Context context;
    private final String code;
    private final String origin;
    private final Listener listener;
    private volatile boolean closed;
    private boolean connected;
    private int failures;

    LanGameController(Context context, String address, String code, Listener listener) throws IOException {
        this.context = context.getApplicationContext();
        this.code = code;
        this.listener = listener;
        server = new ServerSocket(0, 4, InetAddress.getByName(address));
        origin = "http://" + address + ":" + server.getLocalPort();
        worker = new Thread(this::serve, "gentv-game-controller");
        worker.setDaemon(true);
        worker.start();
    }

    String url() { return origin + "/"; }

    static String localAddress() {
        try {
            String fallback = null;
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = nic.getName().toLowerCase(Locale.ROOT);
                if (!nic.isUp() || nic.isLoopback() || name.startsWith("tun") || name.startsWith("ppp")
                        || name.startsWith("rmnet")) continue;
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en"))
                        return address.getHostAddress();
                    if (fallback == null) fallback = address.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception ignored) { return null; }
    }

    private void serve() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                handle(socket);
            } catch (IOException ignored) { if (closed) return; }
        }
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        String request = line(in);
        if (request == null) return;
        String host = null, requestOrigin = null;
        int length = 0;
        for (int i = 0; i < 32; i++) {
            String header = line(in);
            if (header == null) return;
            if (header.isEmpty()) break;
            int colon = header.indexOf(':');
            if (colon < 1) { reply(out, 400, "Bad header", "text/plain"); return; }
            String name = header.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = header.substring(colon + 1).trim();
            if ("host".equals(name)) host = value;
            else if ("origin".equals(name)) requestOrigin = value;
            else if ("content-length".equals(name)) {
                try { length = Integer.parseInt(value); }
                catch (NumberFormatException bad) { reply(out, 400, "Bad length", "text/plain"); return; }
            }
        }
        if (!origin.substring(7).equalsIgnoreCase(host)
                || requestOrigin != null && !origin.equalsIgnoreCase(requestOrigin)) {
            reply(out, 403, "Wrong origin", "text/plain"); return;
        }
        if ("GET / HTTP/1.1".equals(request) || "GET / HTTP/1.0".equals(request)) {
            try (InputStream page = getClass().getClassLoader()
                    .getResourceAsStream("assets/game_remote.html")) {
                if (page == null) throw new IOException("Plugin controller page missing");
                byte[] bytes = new byte[page.available()];
                int read = 0;
                while (read < bytes.length) {
                    int n = page.read(bytes, read, bytes.length - read);
                    if (n < 0) break;
                    read += n;
                }
                reply(out, 200, new String(bytes, 0, read, UTF8), "text/html; charset=UTF-8");
            }
            return;
        }
        boolean connect = "POST /connect HTTP/1.1".equals(request);
        if ((!connect && !"POST /key HTTP/1.1".equals(request)) || length < 1 || length > 128) {
            reply(out, 404, "Not found", "text/plain"); return;
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            int b = in.read();
            if (b < 0) return;
            bytes[i] = (byte) b;
        }
        String[] fields = new String(bytes, UTF8).split("&");
        String submitted = null, key = null, down = null;
        for (String field : fields) {
            int equals = field.indexOf('=');
            if (equals < 0) continue;
            String name = field.substring(0, equals), value = field.substring(equals + 1);
            if ("code".equals(name)) submitted = value;
            else if ("key".equals(name)) key = value;
            else if ("down".equals(name)) down = value;
        }
        if (submitted == null || !MessageDigest.isEqual(code.getBytes(UTF8), submitted.getBytes(UTF8))) {
            if (++failures >= 5) close();
            reply(out, 403, "Wrong TV code", "text/plain"); return;
        }
        if (connect) {
            markConnected();
            reply(out, 200, "OK", "text/plain");
            return;
        }
        if (!("ArrowUp".equals(key) || "ArrowDown".equals(key) || "ArrowLeft".equals(key)
                || "ArrowRight".equals(key) || "Shift".equals(key) || "Space".equals(key)
                || "Enter".equals(key) || "Escape".equals(key) || "Back".equals(key) || "r".equals(key))
                || !("0".equals(down) || "1".equals(down))) {
            reply(out, 400, "Bad key", "text/plain"); return;
        }
        if (!"Back".equals(key) && !listener.ready()) {
            reply(out, 503, "Game loading", "text/plain"); return;
        }
        markConnected();
        listener.key(key, "1".equals(down));
        reply(out, 200, "OK", "text/plain");
    }

    private void markConnected() {
        if (connected) return;
        connected = true;
        listener.connected();
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder result = new StringBuilder();
        while (result.length() < 2048) {
            int b = in.read();
            if (b < 0) return null;
            if (b == '\n') return result.toString();
            if (b != '\r') result.append((char) b);
        }
        return null;
    }

    private static void reply(OutputStream out, int status, String body, String mime) throws IOException {
        byte[] bytes = body.getBytes(UTF8);
        String head = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\n"
                + "Content-Type: " + mime + "\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
                + "\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'"
                + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(ASCII));
        out.write(bytes);
        out.flush();
    }

    @Override public void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
    }
}
