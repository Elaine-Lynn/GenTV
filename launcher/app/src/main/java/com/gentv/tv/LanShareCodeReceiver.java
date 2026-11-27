package com.gentv.tv;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Accepts one browser submission while the TV's Add share code page is visible. */
final class LanShareCodeReceiver implements Closeable {
    interface Listener {
        void received(LanShareCodeReceiver source, ShareCodeLinks.Link link);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_BODY = 16 * 1024;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private final ServerSocket server;
    private final String host;
    private final String token = UUID.randomUUID().toString();
    private final byte[] page;
    private final ShareCodeLinks links;
    private final Listener listener;
    private ShareCodeLinks.Link savedLink;
    private volatile boolean closed;
    private volatile Socket client;

    LanShareCodeReceiver(String address, String form, ShareCodeLinks links, Listener listener) throws IOException {
        this.links = links;
        this.listener = listener;
        page = form.replace("__SESSION_TOKEN__", token).getBytes(UTF8);
        server = new ServerSocket(0, 4, InetAddress.getByName(address));
        host = address + ":" + server.getLocalPort();
    }

    String url() {
        return "http://" + host;
    }

    void start() {
        Thread worker = new Thread(this::serve, "gentv-share-code-receiver");
        worker.setDaemon(true);
        worker.start();
    }

    private void serve() {
        while (!closed) {
            ShareCodeLinks.Link saved = null;
            try (Socket socket = server.accept()) {
                client = socket;
                if (closed) return;
                saved = handle(socket);
            } catch (IOException ignored) {
                // Disconnected or malformed clients can reconnect while the TV is still waiting.
            } finally {
                client = null;
            }
            if (saved != null) {
                close();
                listener.received(this, saved);
            }
        }
    }

    private ShareCodeLinks.Link handle(Socket socket) throws IOException {
        InputStream input = requestInput(socket);
        OutputStream output = socket.getOutputStream();
        String[] request = line(input, 2048).split(" ");
        if (request.length != 3 || !request[2].startsWith("HTTP/1.")) {
            reply(output, 400, "request");
            return null;
        }
        Map<String, String> headers = readHeaders(input);
        String origin = headers.get("origin");
        if (!host.equalsIgnoreCase(headers.get("host")) || (origin != null && !url().equalsIgnoreCase(origin))) {
            reply(output, 403, "session");
            return null;
        }
        if ("GET".equals(request[0]) && "/".equals(request[1])) {
            reply(output, 200, "text/html; charset=UTF-8", page);
            return null;
        }
        if (!"POST".equals(request[0]) || !"/submit".equals(request[1])) {
            reply(output, 404, "request");
            return null;
        }
        Map<String, String> values;
        try {
            values = readForm(input, headers);
        } catch (IllegalArgumentException error) {
            reply(output, 400, "request");
            return null;
        }
        if (!token.equals(values.get("token"))) {
            reply(output, 403, "session");
            return null;
        }
        String name = values.containsKey("name") ? values.get("name").trim() : "";
        String url = values.containsKey("url") ? ShareCodeLinks.webUrl(values.get("url").trim()) : null;
        if (!ShareCodeLinks.validName(name) || url == null) {
            reply(output, 400, url == null ? "url" : "name");
            return null;
        }
        ShareCodeLinks.Link saved;
        try {
            synchronized (this) {
                // A submission and page shutdown must agree on which one completed first.
                if (closed) return null;
                savedLink = saved = links.add(name, url);
            }
        } catch (IOException error) {
            reply(output, 500, "storage");
            return null;
        }
        if (saved == null) {
            reply(output, 409, "full");
            return null;
        }
        // The TV still shows the saved result if the browser disconnects before reading the response.
        try {
            reply(output, 200, ShareCodeLinks.formatted(saved.code));
        } catch (IOException ignored) { }
        return saved;
    }

    private static InputStream requestInput(Socket socket) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_TIMEOUT_MS);
        // The receiver handles one client at a time. Bound the whole request even if bytes keep arriving.
        return new BufferedInputStream(new FilterInputStream(socket.getInputStream()) {
            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("Share request timed out");
                socket.setSoTimeout((int) Math.min(READ_TIMEOUT_MS, TimeUnit.NANOSECONDS.toMillis(remaining) + 1L));
                return super.read(bytes, offset, length);
            }
        });
    }

    private static Map<String, String> readHeaders(InputStream input) throws IOException {
        Map<String, String> headers = new HashMap<>();
        int remaining = 8192;
        while (remaining > 0) {
            String header = line(input, 2048);
            remaining -= header.length() + 2;
            if (remaining < 0) break;
            if (header.isEmpty()) return headers;
            int colon = header.indexOf(':');
            if (colon <= 0 || headers.put(header.substring(0, colon).toLowerCase(Locale.ROOT),
                    header.substring(colon + 1).trim()) != null) break;
        }
        throw new IOException("Invalid HTTP headers");
    }

    private static Map<String, String> readForm(InputStream input, Map<String, String> headers) throws IOException {
        String type = headers.get("content-type");
        if (type == null || !"application/x-www-form-urlencoded".equalsIgnoreCase(type.split(";", 2)[0])
                || headers.containsKey("transfer-encoding")) throw new IllegalArgumentException();
        int size = Integer.parseInt(headers.get("content-length"));
        if (size <= 0 || size > MAX_BODY) throw new IllegalArgumentException();
        byte[] body = new byte[size];
        for (int offset = 0; offset < size;) {
            int count = input.read(body, offset, size - offset);
            if (count < 0) throw new IOException("Incomplete form");
            offset += count;
        }
        Map<String, String> fields = new HashMap<>();
        for (String field : new String(body, UTF8).split("&")) {
            int equals = field.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException();
            String key = URLDecoder.decode(field.substring(0, equals), "UTF-8");
            String value = URLDecoder.decode(field.substring(equals + 1), "UTF-8");
            if (fields.put(key, value) != null) throw new IllegalArgumentException();
        }
        return fields;
    }

    private static String line(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() < limit) {
            int next = input.read();
            if (next < 0 || next > 127 || next == 0) break;
            if (next == '\n') {
                String line = bytes.toString("US-ASCII");
                return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            }
            bytes.write(next);
        }
        throw new IOException("Invalid HTTP line");
    }

    private static void reply(OutputStream output, int status, String body) throws IOException {
        reply(output, status, "text/plain; charset=UTF-8", body.getBytes(UTF8));
    }

    private static void reply(OutputStream output, int status, String type, byte[] body) throws IOException {
        String headers = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error")
                + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
                + "\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; "
                + "script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'"
                + "\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(UTF8));
        output.write(body);
        output.flush();
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        try { server.close(); } catch (IOException ignored) { }
        Socket socket = client;
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }

    synchronized ShareCodeLinks.Link savedLink() {
        return savedLink;
    }
}
