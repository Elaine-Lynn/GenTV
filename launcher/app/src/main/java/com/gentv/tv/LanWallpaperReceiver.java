package com.gentv.tv;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Temporary HTTP receiver bound to one LAN address while its TV sheet remains open. The page and the upload live
 * under a short random path, so the address the TV shows (and its QR code) is the only key: nobody else on the
 * network can send a file without it, and nobody has to type a separate code.
 */
final class LanWallpaperReceiver implements Closeable {
    interface Listener { void uploaded(File file, String mime); }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long IMAGE_LIMIT = 4L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 30L * 1024 * 1024;
    private final ServerSocket server;
    private final File stagingDir;
    /** Letters and digits that cannot be misread on a TV or typed wrong: no 0/o, 1/l/i. */
    private static final String TOKEN_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int MAX_REJECTED = 12;
    private final String token;
    private final String origin;
    private final Listener listener;
    private final Thread worker;
    private volatile boolean closed;
    private volatile Socket client;
    private int rejected;

    /** A fresh path secret for one transfer sheet. */
    static String newToken(SecureRandom random) {
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 4; i++) token.append(TOKEN_ALPHABET.charAt(random.nextInt(TOKEN_ALPHABET.length())));
        return token.toString();
    }

    LanWallpaperReceiver(String address, String token, File stagingDir, Listener listener) throws IOException {
        if (token == null || !token.matches("[" + TOKEN_ALPHABET + "]{4,}")) throw new IOException("Invalid token");
        server = new ServerSocket(0, 1, InetAddress.getByName(address));
        this.stagingDir = stagingDir;
        this.token = token;
        origin = "http://" + address + ":" + server.getLocalPort();
        this.listener = listener;
        worker = new Thread(this::serve, "gentv-wallpaper-receiver");
        worker.setDaemon(true);
        worker.start();
    }

    /** The page's full address, the key included. */
    String url() { return origin + "/" + token; }

    private void serve() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                client = socket;
                socket.setSoTimeout(30_000);
                handle(socket);
            } catch (IOException ignored) {
                if (closed) break;
            } finally { client = null; }
        }
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        String request = line(in, 2048);
        if (request == null) return;
        String[] parts = request.split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/1.")) { reply(out, 400, "Bad request"); return; }
        Map<String, String> headers = new HashMap<>();
        int headerBytes = 0;
        while (true) {
            String header = line(in, 2048);
            if (header == null) return;
            headerBytes += header.length() + 2;
            if (headerBytes > 8192) { reply(out, 431, "Headers too large"); return; }
            if (header.isEmpty()) break;
            int colon = header.indexOf(':');
            if (colon <= 0 || headers.put(header.substring(0, colon).toLowerCase(Locale.ROOT),
                    header.substring(colon + 1).trim()) != null) {
                reply(out, 400, "Bad header"); return;
            }
        }
        if (!origin.substring(7).equalsIgnoreCase(headers.get("host"))
                || (headers.containsKey("origin") && !origin.equalsIgnoreCase(headers.get("origin")))) {
            reply(out, 403, "Wrong origin"); return;
        }
        String page = "/" + token;
        if ("GET".equals(parts[0]) && "/favicon.ico".equals(parts[1])) { reply(out, 404, "Not found"); return; }
        if ("GET".equals(parts[0]) && matches(parts[1], page)) {
            reply(out, 200, page(), "text/html; charset=UTF-8"); return;
        }
        if (!"PUT".equals(parts[0]) || !matches(parts[1], page + "/upload")) {
            // Guessing the path: after a dozen misses the sheet's receiver stops.
            rejected++;
            reply(out, 404, "Not found");
            if (rejected >= MAX_REJECTED) close();
            return;
        }
        String mime = headers.get("content-type");
        long limit = ("video/mp4".equals(mime) || "video/x-m4v".equals(mime)
                || "video/webm".equals(mime) || "video/x-matroska".equals(mime)
                || "video/matroska".equals(mime)) ? VIDEO_LIMIT
                : ("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime))
                ? IMAGE_LIMIT : 0;
        if (limit == 0) { reply(out, 415, "JPEG, PNG, WebP, MP4, M4V, WebM or MKV only"); return; }
        long length;
        try { length = Long.parseLong(headers.get("content-length")); }
        catch (Exception invalid) { reply(out, 411, "Content-Length required"); return; }
        if (length <= 0 || length > limit) { reply(out, 413, "File too large"); return; }
        if (!stagingDir.isDirectory() && !stagingDir.mkdirs()) { reply(out, 500, "Storage unavailable"); return; }
        File file = File.createTempFile("lan-wallpaper-", ".upload", stagingDir);
        boolean complete = false;
        try {
            try (FileOutputStream target = new FileOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                long remaining = length;
                while (remaining > 0) {
                    int count = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (count < 0) throw new IOException("Incomplete upload");
                    target.write(buffer, 0, count);
                    remaining -= count;
                }
            }
            reply(out, 200, "Received");
            complete = true;
            listener.uploaded(file, "video/x-m4v".equals(mime) ? "video/mp4"
                    : "video/matroska".equals(mime) ? "video/x-matroska" : mime);
        } finally {
            if (!complete) file.delete();
        }
    }

    private static boolean matches(String requested, String expected) {
        return MessageDigest.isEqual(requested.getBytes(UTF8), expected.getBytes(UTF8));
    }

    /**
     * One quiet screen: a title and a single card. Choosing a file sends it at once, with the progress along the
     * card's lower edge and the picture (or the playing video) filling the card. Chinese or English by the
     * browser's language; limits are only mentioned when a file breaks them.
     */
    private static String page() {
        return """
                <!doctype html>
                <html lang='zh-CN'>
                <head>
                <meta charset='utf-8'>
                <meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>
                <meta name='theme-color' content='#08090d'>
                <title>GenTV</title>
                <style>
                :root{color-scheme:dark;--ink:#f4f6fa;--mute:#8b93a5;--line:#ffffff1c}
                *{box-sizing:border-box;margin:0}
                html,body{height:100%}
                body{display:flex;align-items:center;justify-content:center;min-height:100%;padding:72px 24px calc(48px + env(safe-area-inset-bottom));background:radial-gradient(110% 60% at 50% -8%,#1a2231 0,#08090d 64%) #08090d;color:var(--ink);font:15px/1.5 -apple-system,BlinkMacSystemFont,'PingFang SC','Helvetica Neue','Segoe UI',system-ui,sans-serif;-webkit-font-smoothing:antialiased;-webkit-tap-highlight-color:transparent}
                .mark{position:fixed;top:calc(26px + env(safe-area-inset-top));left:0;right:0;text-align:center;font-size:11px;font-weight:600;letter-spacing:.42em;text-indent:.42em;color:#ffffff4d}
                main{width:100%;max-width:400px}
                h1{font-size:24px;font-weight:600;letter-spacing:-.01em;text-align:center}
                .card{position:relative;display:block;margin-top:32px;aspect-ratio:16/10;border-radius:26px;background:#ffffff08;border:1px solid var(--line);overflow:hidden;cursor:pointer;transition:border-color .3s,background-color .3s,transform .3s cubic-bezier(.2,.8,.2,1)}
                .card:hover,.card.over{border-color:#ffffff3d;background-color:#ffffff0f}
                .card:active{transform:scale(.985)}
                .card:focus-within{outline:2px solid #ffffffb3;outline-offset:5px}
                .card.busy{cursor:default}
                input{position:absolute;inset:0;width:100%;height:100%;opacity:0;cursor:inherit}
                .empty{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;color:var(--mute);font-size:14px;pointer-events:none;transition:opacity .3s}
                .plus{display:grid;place-items:center;width:54px;height:54px;border-radius:50%;border:1px solid #ffffff33;color:var(--ink)}
                .media{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;pointer-events:none;animation:in .5s ease both}
                .shade{position:absolute;inset:0;background:linear-gradient(180deg,transparent 45%,#000000b3);pointer-events:none}
                .meta{position:absolute;left:20px;right:20px;bottom:17px;display:flex;justify-content:space-between;align-items:baseline;gap:14px;font-size:13px;color:#ffffffe6;pointer-events:none}
                .name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .pct{font-variant-numeric:tabular-nums;color:#ffffffb3}
                .bar{position:absolute;left:0;bottom:0;height:2px;width:0;background:var(--ink);transition:width .25s ease,opacity .5s ease;pointer-events:none}
                .status{min-height:22px;margin-top:22px;text-align:center;font-size:14px;color:var(--mute);transition:color .3s}
                .status.ok{color:#9be8bf}.status.err{color:#ff9e91}
                [hidden]{display:none!important}
                @keyframes in{from{opacity:0;transform:scale(1.03)}to{opacity:1;transform:none}}
                @media(prefers-reduced-motion:reduce){*{transition:none!important;animation:none!important}}
                </style>
                </head>
                <body>
                <div class='mark'>GENTV</div>
                <main>
                <h1 id='title'>传壁纸到电视</h1>
                <label class='card' id='card'>
                <input id='file' type='file' accept='image/jpeg,image/png,image/webp,video/mp4,video/x-m4v,video/webm,video/x-matroska,video/matroska,.m4v,.mkv'>
                <div class='empty' id='empty'><span class='plus'><svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.5' stroke-linecap='round'><path d='M12 5v14M5 12h14'/></svg></span><span id='pick'>选择图片或视频</span></div>
                <img class='media' id='img' alt='' hidden>
                <video class='media' id='vid' muted playsinline loop autoplay hidden></video>
                <div class='shade' id='shade' hidden></div>
                <div class='meta' id='meta' hidden><span class='name' id='name'></span><span class='pct' id='pct'></span></div>
                <i class='bar' id='bar'></i>
                </label>
                <p class='status' id='status' role='status' aria-live='polite'></p>
                </main>
                <script>
                const zh=/^zh/i.test(navigator.language||'zh');
                const T=zh?{title:'传壁纸到电视',pick:'选择图片或视频',sending:'正在传输',done:'已传到电视 · 可继续选择',type:'不支持这种格式',big:'文件太大 · 图片 4 MB、视频 30 MB 以内',fail:'传输失败，请重试',lost:'电视已关闭传输，请重新打开'}
                :{title:'Send to your TV',pick:'Choose a picture or video',sending:'Sending',done:'On your TV · Choose another',type:'This format is not supported',big:'Too large · up to 4 MB for pictures, 30 MB for videos',fail:'Could not send. Try again.',lost:'Transfer closed on the TV. Open it again.'};
                document.documentElement.lang=zh?'zh-CN':'en';
                const $=id=>document.getElementById(id);
                $('title').textContent=T.title;$('pick').textContent=T.pick;document.title='GenTV · '+T.title;
                const card=$('card'),input=$('file'),img=$('img'),vid=$('vid'),bar=$('bar'),status=$('status');
                const types={jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp',mp4:'video/mp4',m4v:'video/x-m4v',webm:'video/webm',mkv:'video/x-matroska'};
                let url=null,busy=false;
                function say(text,kind){status.textContent=text||'';status.className='status'+(kind?' '+kind:'')}
                function mimeOf(f){const t=f.type&&f.type!=='application/octet-stream'?f.type:types[(f.name.split('.').pop()||'').toLowerCase()];return t||''}
                function show(f,mime){
                  if(url)URL.revokeObjectURL(url);url=URL.createObjectURL(f);
                  const video=mime.startsWith('video/');
                  img.hidden=video;vid.hidden=!video;
                  if(video){img.removeAttribute('src');vid.src=url}else{vid.removeAttribute('src');vid.load();img.src=url}
                  $('empty').hidden=true;$('shade').hidden=false;$('meta').hidden=false;$('name').textContent=f.name;
                }
                function send(f){
                  if(busy||!f)return;
                  const mime=mimeOf(f);
                  if(!Object.values(types).concat('video/matroska').includes(mime)){say(T.type,'err');return}
                  if(!f.size||f.size>(mime.startsWith('video/')?31457280:4194304)){say(T.big,'err');return}
                  show(f,mime);busy=true;card.classList.add('busy');input.disabled=true;
                  bar.style.opacity=1;bar.style.width='0';$('pct').textContent='0%';say(T.sending);
                  const r=new XMLHttpRequest();r.open('PUT',location.pathname.replace(/[/]$/,'')+'/upload');
                  r.setRequestHeader('Content-Type',mime);
                  r.upload.onprogress=e=>{if(e.lengthComputable){const p=Math.round(e.loaded/e.total*100);bar.style.width=p+'%';$('pct').textContent=p+'%'}};
                  const end=()=>{busy=false;card.classList.remove('busy');input.disabled=false;input.value=''};
                  r.onload=()=>{end();
                    if(r.status===200){bar.style.width='100%';$('pct').textContent='✓';say(T.done,'ok');setTimeout(()=>{bar.style.opacity=0},900)}
                    else{bar.style.width='0';$('pct').textContent='';say(r.status===413?T.big:r.status===415?T.type:r.status===404?T.lost:T.fail,'err')}};
                  r.onerror=()=>{end();bar.style.width='0';$('pct').textContent='';say(T.lost,'err')};
                  r.send(f);
                }
                input.addEventListener('change',()=>send(input.files[0]));
                ['dragenter','dragover'].forEach(n=>card.addEventListener(n,e=>{e.preventDefault();if(!busy)card.classList.add('over')}));
                ['dragleave','drop'].forEach(n=>card.addEventListener(n,e=>{e.preventDefault();card.classList.remove('over')}));
                card.addEventListener('drop',e=>{if(e.dataTransfer.files.length)send(e.dataTransfer.files[0])});
                </script>
                </body>
                </html>
                """;
    }

    private static String line(InputStream in, int limit) throws IOException {
        StringBuilder result = new StringBuilder();
        while (result.length() < limit) {
            int value = in.read();
            if (value < 0) return null;
            if (value == '\n') {
                int end = result.length();
                if (end > 0 && result.charAt(end - 1) == '\r') result.setLength(end - 1);
                return result.toString();
            }
            if (value > 127 || value == 0) throw new IOException("Invalid header");
            result.append((char) value);
        }
        throw new IOException("Header too long");
    }

    private static void reply(OutputStream out, int status, String body) throws IOException {
        reply(out, status, body, "text/plain; charset=UTF-8");
    }

    private static void reply(OutputStream out, int status, String body, String type) throws IOException {
        byte[] payload = body.getBytes(UTF8);
        String header = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: " + payload.length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
                + "\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src blob:; media-src blob:"
                + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(UTF8));
        out.write(payload);
        out.flush();
    }

    @Override public void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        if (Thread.currentThread() != worker) {
            Socket socket = client;
            if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
