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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Temporary HTTP receiver bound to one LAN address while its TV dialog remains open. */
final class LanWallpaperReceiver implements Closeable {
    interface Listener { void uploaded(File file, String mime); }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long IMAGE_LIMIT = 4L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 30L * 1024 * 1024;
    private final ServerSocket server;
    private final File stagingDir;
    private final String code;
    private final String origin;
    private final Listener listener;
    private final Thread worker;
    private volatile boolean closed;
    private volatile Socket client;
    private int rejectedCodes;

    LanWallpaperReceiver(String address, String code, File stagingDir, Listener listener) throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getByName(address));
        this.stagingDir = stagingDir;
        this.code = code;
        origin = "http://" + address + ":" + server.getLocalPort();
        this.listener = listener;
        worker = new Thread(this::serve, "gentv-wallpaper-receiver");
        worker.setDaemon(true);
        worker.start();
    }

    String url() { return origin + "/"; }

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
        if ("GET".equals(parts[0]) && "/".equals(parts[1])) {
            reply(out, 200, page(), "text/html; charset=UTF-8"); return;
        }
        if (!"PUT".equals(parts[0]) || !"/upload".equals(parts[1])) {
            reply(out, 404, "Not found"); return;
        }
        String submitted = headers.get("x-gentv-code");
        if (submitted == null || !MessageDigest.isEqual(code.getBytes(UTF8), submitted.getBytes(UTF8))) {
            rejectedCodes++;
            reply(out, 403, rejectedCodes >= 5 ? "Too many attempts / 尝试过多" : "Wrong code / 验证码错误");
            if (rejectedCodes >= 5) close();
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
            reply(out, 200, "Upload received. Adding to your wallpapers. / 传输完成，正在加入自定义壁纸列表。");
            complete = true;
            listener.uploaded(file, "video/x-m4v".equals(mime) ? "video/mp4"
                    : "video/matroska".equals(mime) ? "video/x-matroska" : mime);
        } finally {
            if (!complete) file.delete();
        }
    }

    private static String page() {
        return """
                <!doctype html>
                <html lang='zh-CN'>
                <head>
                  <meta charset='utf-8'>
                  <meta name='viewport' content='width=device-width,initial-scale=1'>
                  <meta name='theme-color' content='#0c1220'>
                  <title>GenTV · 壁纸传输</title>
                  <style>
                    :root{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#eef3ff;background:#0c1220;font-synthesis:none}
                    *{box-sizing:border-box}body{min-height:100vh;margin:0;background:radial-gradient(circle at 15% 8%,#193859 0,transparent 36%),radial-gradient(circle at 95% 90%,#202b59 0,transparent 35%),#0c1220}
                    body:before{content:'';position:fixed;inset:0;pointer-events:none;opacity:.2;background-image:linear-gradient(#fff1 1px,transparent 1px),linear-gradient(90deg,#fff1 1px,transparent 1px);background-size:56px 56px;mask-image:linear-gradient(to bottom,#000,transparent 80%)}
                    .shell{position:relative;max-width:1060px;margin:auto;padding:40px 24px 56px}
                    .brand{display:flex;align-items:center;gap:12px;font-size:20px;font-weight:800;letter-spacing:-.04em}
                    .brand-mark{display:grid;place-items:center;width:36px;height:36px;border-radius:11px;background:linear-gradient(140deg,#70d8e8,#7785ff);color:#091522;font-size:23px;box-shadow:0 6px 24px #63b9fb50}
                    .brand span:last-child{color:#9aa9c4;font-size:11px;font-weight:700;letter-spacing:.18em;margin-left:4px}
                    .layout{display:grid;grid-template-columns:minmax(0,.9fr) minmax(0,1.1fr);gap:56px;align-items:center;margin-top:88px}
                    .eyebrow{color:#8addec;font-size:12px;font-weight:800;letter-spacing:.2em;text-transform:uppercase}
                    h1{font-size:clamp(38px,5.2vw,62px);line-height:1.12;letter-spacing:-.06em;margin:18px 0 20px}
                    h1 em{font-style:normal;background:linear-gradient(100deg,#81dfe7,#aab0ff);background-clip:text;-webkit-background-clip:text;color:transparent}
                    .intro{max-width:420px;color:#aab8d0;font-size:16px;line-height:1.8;margin:0}
                    .intro-en{color:#71819d;font-size:13px;line-height:1.6;margin:10px 0 0}
                    .steps{display:grid;gap:17px;margin-top:42px}
                    .step{display:flex;align-items:center;gap:14px;color:#bdc9dc;font-size:14px}
                    .step b{display:grid;place-items:center;flex:none;width:28px;height:28px;border:1px solid #41536f;border-radius:50%;color:#9dd7e8;font-size:12px}
                    .card{padding:28px;background:linear-gradient(145deg,#1a263aef,#141d2eef);border:1px solid #35455d;border-radius:24px;box-shadow:0 22px 70px #0006, inset 0 1px #ffffff12}
                    .card-head{display:flex;justify-content:space-between;gap:12px;align-items:start;margin-bottom:22px}
                    .card h2{font-size:20px;letter-spacing:-.03em;margin:0 0 5px}.muted{color:#91a1ba;font-size:12px;margin:0}
                    .badge{white-space:nowrap;border:1px solid #33576c;background:#193e4b;color:#98e6e8;padding:6px 9px;border-radius:99px;font-size:11px;font-weight:700}
                    .drop{display:flex;min-height:215px;align-items:center;justify-content:center;flex-direction:column;text-align:center;gap:8px;padding:22px;border:1.5px dashed #5c7494;border-radius:17px;background:#111b2c;cursor:pointer;transition:background .2s,border-color .2s,transform .2s}
                    .drop:hover,.drop.dragging{background:#1c3049;border-color:#8addec;transform:translateY(-2px)}.drop:focus-within{outline:3px solid #8addec;outline-offset:3px}
                    .file-input{position:absolute;width:1px;height:1px;opacity:0;overflow:hidden}[hidden]{display:none!important}
                    .file-icon{display:grid;place-items:center;width:48px;height:48px;border-radius:15px;background:linear-gradient(135deg,#2b6375,#4158a5);color:#c5f9ff;font-size:27px;font-weight:300;margin-bottom:4px}
                    .drop strong{font-size:16px;font-weight:700}.drop small{color:#8e9db6;font-size:12px}.drop .select{color:#83d9ea}
                    .preview{max-width:100%;max-height:140px;border-radius:10px;object-fit:contain}.preview[hidden]{display:none}
                    .file-name{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.limits{display:flex;gap:8px;flex-wrap:wrap;margin:15px 0 25px}
                    .limits span{padding:6px 10px;border-radius:8px;background:#ffffff0b;color:#adbad0;font-size:11px}
                    .field-label{display:flex;align-items:baseline;justify-content:space-between;gap:8px;font-size:14px;font-weight:700;margin-bottom:9px}.field-label small{color:#8798b4;font-size:11px;font-weight:400}
                    .code{width:100%;height:52px;padding:0 15px;border:1px solid #4b5e78;border-radius:11px;background:#0e1829;color:#fff;font-family:inherit;font-size:20px;font-weight:600;letter-spacing:.24em;outline:none}
                    .code:focus{border-color:#8addec;box-shadow:0 0 0 3px #8addec22}.code::placeholder{color:#65758e;letter-spacing:.15em}
                    .hint{color:#8798b4;font-size:12px;line-height:1.5;margin:9px 0 23px}
                    button{width:100%;min-height:52px;border:0;border-radius:12px;background:linear-gradient(100deg,#80dce9,#9da7ff);color:#0c1830;font-family:inherit;font-size:15px;font-weight:800;cursor:pointer;box-shadow:0 8px 22px #6da8ef30;transition:filter .2s,transform .2s}
                    button:hover{filter:brightness(1.08);transform:translateY(-1px)}button:disabled{opacity:.65;cursor:wait;transform:none}
                    .progress{height:5px;margin-top:16px;border-radius:99px;background:#344154;overflow:hidden}.progress[hidden]{display:none}.progress i{display:block;width:0;height:100%;background:#88dfea;transition:width .18s}
                    .status{min-height:20px;margin:13px 0 0;color:#9dacbf;font-size:12px;line-height:1.5}.status.error{color:#ffb0a7}.status.success{color:#9cecc9}
                    .foot{display:flex;justify-content:space-between;gap:12px;margin-top:35px;color:#6d7c96;font-size:11px}
                    @media(max-width:760px){.shell{padding:24px 18px 36px}.layout{grid-template-columns:1fr;gap:32px;margin-top:50px}.steps{grid-template-columns:repeat(3,1fr);gap:8px;margin-top:26px}.step{display:block;font-size:11px;line-height:1.5}.step b{margin-bottom:8px}.card{padding:20px;border-radius:20px}.foot{margin-top:25px}}
                    @media(max-width:430px){h1{font-size:39px}.badge{display:none}.steps{grid-template-columns:1fr;gap:10px}.step{display:flex;align-items:center;font-size:12px}.step b{margin:0}.drop{min-height:190px}}
                    @media(prefers-reduced-motion:reduce){*,*:before,*:after{transition:none!important}}
                  </style>
                </head>
                <body>
                  <main class='shell'>
                    <div class='brand'><span class='brand-mark'>G</span>GenTV <span>WALLPAPER STUDIO</span></div>
                    <div class='layout'>
                      <section aria-labelledby='title'>
                        <div class='eyebrow'>YOUR TV, YOUR STYLE</div>
                        <h1 id='title'>把喜欢的画面<br><em>送上电视。</em></h1>
                        <p class='intro'>从手机或电脑上传图片、视频，传输完成后会加入自定义壁纸列表，不会自动更换当前壁纸。</p>
                        <p class='intro-en'>Upload from your phone or computer. The file appears in your collection without changing your current wallpaper.</p>
                        <div class='steps'><div class='step'><b>01</b>选择图片或视频</div><div class='step'><b>02</b>输入电视验证码</div><div class='step'><b>03</b>上传后在列表中查看</div></div>
                      </section>
                      <form class='card' id='form'>
                        <div class='card-head'><div><h2>传输壁纸</h2><p class='muted'>Upload wallpaper to GenTV</p></div><span class='badge'>局域网传输</span></div>
                        <label class='drop' id='drop' for='file'>
                          <input class='file-input' id='file' type='file' accept='image/jpeg,image/png,image/webp,video/mp4,video/x-m4v,video/webm,video/x-matroska,video/matroska,.m4v,.mkv' aria-label='选择图片或视频'>
                          <span class='file-icon' id='file-icon'>↑</span><img class='preview' id='preview' alt='所选图片预览' hidden>
                          <strong class='file-name' id='file-name'>点击选择文件<span class='select'>，或拖放到这里</span></strong>
                          <small id='file-detail'>JPG、PNG、WebP 图片或 MP4、M4V、WebM、MKV 视频</small>
                        </label>
                        <div class='limits'><span>图片 ≤ 4 MB</span><span>视频 ≤ 30 MB</span><span>可连续上传多个文件</span></div>
                        <label class='field-label' for='code'>电视验证码 <small>TV verification code</small></label>
                        <input class='code' id='code' inputmode='numeric' pattern='[0-9]*' maxlength='4' autocomplete='off' placeholder='4 位数字' aria-describedby='code-hint'>
                        <p class='hint' id='code-hint'>扫描电视二维码时会自动填写；请保持电视接收页面打开。</p>
                        <button id='send' type='submit'>上传到电视 / Upload →</button>
                        <div class='progress' id='progress' hidden><i id='progress-fill'></i></div>
                        <p class='status' id='status' role='status' aria-live='polite'></p>
                      </form>
                    </div>
                    <div class='foot'><span>GenTV · Local transfer</span><span>仅在当前局域网内传输 · No cloud upload</span></div>
                  </main>
                  <script>
                    const fileInput=document.getElementById('file'),drop=document.getElementById('drop'),code=document.getElementById('code');
                    const status=document.getElementById('status'),preview=document.getElementById('preview');
                    const progress=document.getElementById('progress'),fill=document.getElementById('progress-fill'),send=document.getElementById('send');
                    const types={jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp',mp4:'video/mp4',m4v:'video/x-m4v',webm:'video/webm',mkv:'video/x-matroska',matroska:'video/matroska'};
                    let selected=null,previewUrl=null,busy=false;
                    const qr=location.hash.match(/^#code=([0-9]{4})$/);
                    if(qr){code.value=qr[1];history.replaceState(null,'',location.pathname+location.search)}
                    function message(text,kind){status.textContent=text;status.className='status'+(kind?' '+kind:'')}
                    function mimeOf(file){const extension=file.name.split('.').pop().toLowerCase();return (!file.type||file.type==='application/octet-stream'?types[extension]:file.type)||''}
                    function validate(file){
                      if(!file){message('请选择文件 / Choose a file','error');return null}
                      const mime=mimeOf(file),limit=mime.startsWith('video/')?31457280:4194304;
                      if(!Object.values(types).includes(mime)){message('不支持的格式，请选择 JPG、PNG、WebP、MP4、M4V、WebM 或 MKV / Unsupported format','error');return null}
                      if(!file.size||file.size>limit){message('文件过大或为空 / File too large or empty','error');return null}
                      return mime;
                    }
                    function showFile(file){
                      selected=file;
                      if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl=null}
                      preview.hidden=true;preview.removeAttribute('src');
                      if(!file)return;
                      document.getElementById('file-name').textContent=file.name;
                      document.getElementById('file-detail').textContent=(file.size/1048576).toFixed(1)+' MB · '+(mimeOf(file).startsWith('video/')?'视频 / Video':'图片 / Image');
                      document.getElementById('file-icon').hidden=!mimeOf(file).startsWith('video/');
                      const mime=validate(file);if(!mime)return;
                      if(!mime.startsWith('video/')){previewUrl=URL.createObjectURL(file);preview.src=previewUrl;preview.hidden=false}
                      message('已选择文件 / File ready');
                    }
                    fileInput.addEventListener('change',()=>showFile(fileInput.files[0]));
                    ['dragenter','dragover'].forEach(name=>drop.addEventListener(name,event=>{event.preventDefault();drop.classList.add('dragging')}));
                    ['dragleave','drop'].forEach(name=>drop.addEventListener(name,event=>{event.preventDefault();drop.classList.remove('dragging')}));
                    drop.addEventListener('drop',event=>{if(!busy&&event.dataTransfer.files.length)showFile(event.dataTransfer.files[0])});
                    document.getElementById('form').addEventListener('submit',event=>{
                      event.preventDefault();if(busy)return;
                      const mime=validate(selected);if(!mime)return;
                      if(!/^[0-9]{4}$/.test(code.value)){message('请输入电视上的 4 位验证码 / Enter the 4-digit TV code','error');code.focus();return}
                      busy=true;send.disabled=true;send.textContent='正在传输 / Uploading…';progress.hidden=false;fill.style.width='0%';
                      message('正在上传，请保持页面打开 / Keep this page open');
                      const request=new XMLHttpRequest();request.open('PUT','/upload');
                      request.setRequestHeader('Content-Type',mime);request.setRequestHeader('X-GenTV-Code',code.value);
                      request.upload.onprogress=event=>{if(event.lengthComputable){const percent=Math.round(event.loaded/event.total*100);fill.style.width=percent+'%';message('正在上传 '+percent+'% / Uploading '+percent+'%')}};
                      request.onload=()=>{busy=false;send.disabled=false;send.textContent='上传到电视 / Upload →';
                        if(request.status===200){fill.style.width='100%';message('传输完成，正在加入壁纸列表 / Upload received. Adding to your collection.','success')}
                        else{progress.hidden=true;message(request.responseText||'上传失败 / Upload failed','error')}};
                      request.onerror=()=>{busy=false;send.disabled=false;send.textContent='上传到电视 / Upload →';progress.hidden=true;message('连接失败，请保持电视接收页面打开 / Connection failed','error')};
                      request.send(selected);
                    });
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
                + "\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src blob:"
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
