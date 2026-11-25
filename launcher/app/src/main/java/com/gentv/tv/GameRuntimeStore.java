package com.gentv.tv;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/** Private, versioned game code and native libraries. No package installation is involved. */
@android.annotation.TargetApi(21)
final class GameRuntimeStore {
    private GameRuntimeStore() { }

    static String abi() {
        for (String candidate : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(candidate) || "armeabi-v7a".equals(candidate)) return candidate;
        }
        return null;
    }

    static long version(Context context) { return activeVersion(context.getFilesDir()); }

    static long activeVersion(File files) {
        try (InputStream in = new android.util.AtomicFile(new File(files, "game-runtime/active-" + abi())).openRead()) {
            byte[] bytes = new byte[32];
            int count = in.read(bytes);
            long version = Long.parseLong(new String(bytes, 0, count, java.nio.charset.StandardCharsets.US_ASCII).trim());
            return version > 0 ? version : 0;
        } catch (Exception unavailable) { return 0; }
    }

    static File directory(Context context) {
        return new File(context.getFilesDir(), "game-runtime/plugins/" + version(context) + "/" + abi());
    }

    static File archive(Context context) {
        return new File(directory(context), "game-" + abi().replace('-', '_') + ".apk");
    }

    static boolean ready(Context context) {
        if (abi() == null || version(context) <= 0 || archive(context).length() <= 0) return false;
        java.util.Properties metadata = new java.util.Properties();
        try (InputStream in = new FileInputStream(new File(directory(context), "game_ready"))) {
            metadata.load(in);
            return GamePluginVersion.compatible(version(context), Integer.parseInt(metadata.getProperty("api")),
                    Long.parseLong(metadata.getProperty("minHost")), Long.parseLong(metadata.getProperty("maxHost")),
                    BuildConfig.VERSION_CODE);
        } catch (Exception invalid) { return false; }
    }

    static void stage(Context context, File downloadedArchive, org.json.JSONObject manifest) throws Exception {
        android.content.pm.PackageInfo info = context.getPackageManager().getPackageArchiveInfo(downloadedArchive.getPath(), 0);
        if (info == null || info.versionCode < version(context)) throw new IOException("Plugin downgrade refused");
        File directory = new File(context.getFilesDir(), "game-runtime/plugins/" + info.versionCode + "/" + abi());
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create game directory");
        File destination = new File(directory, "game-" + abi().replace('-', '_') + ".apk");
        File pending = new File(directory, "game.part");
        copyToPending(downloadedArchive, pending);
        if (!pending.renameTo(destination)) throw new IOException("Cannot stage game archive");
        try (FileOutputStream out = new FileOutputStream(new File(directory, "game_ready"))) {
            java.util.Properties metadata = new java.util.Properties();
            metadata.setProperty("api", Integer.toString(manifest.getInt("hostApiVersion")));
            metadata.setProperty("minHost", Long.toString(manifest.getLong("minHostVersionCode")));
            metadata.setProperty("maxHost", Long.toString(manifest.getLong("maxHostVersionCode")));
            metadata.store(out, null); out.getFD().sync();
        }
        // Kill only this app's old game processes before changing the shared active pointer.
        android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.uid == android.os.Process.myUid() && process.pid != android.os.Process.myPid()
                    && process.processName.startsWith(context.getPackageName() + ":")
                    && process.processName.substring(context.getPackageName().length() + 1)
                        .matches("game_runtime|tab[0-9]+|media|gmplugin|socket|gpu|utility|ipdlunittest"))
                android.os.Process.killProcess(process.pid);
        }
        android.util.AtomicFile active = new android.util.AtomicFile(new File(context.getFilesDir(), "game-runtime/active-" + abi()));
        FileOutputStream out = active.startWrite();
        try {
            out.write(Long.toString(info.versionCode).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            active.finishWrite(out);
        } catch (IOException error) { active.failWrite(out); throw error; }
    }

    private static void copyToPending(File source, File pending) throws IOException {
        if (pending.exists() && !pending.delete()) throw new IOException("Cannot replace pending game archive");
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(pending)) {
            byte[] bytes = new byte[32768];
            int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            out.getFD().sync();
        }
        if (!pending.setReadOnly()) throw new IOException("Cannot protect game archive");
    }

    static DexClassLoader loader(Context context) throws IOException {
        if (!ready(context)) throw new IOException("Game archive missing");
        File nativeDirectory = new File(context.getCodeCacheDir(),
                "game-runtime-libs/" + version(context) + "/" + abi());
        if (!nativeDirectory.isDirectory() && !nativeDirectory.mkdirs())
            throw new IOException("Cannot create game native directory");
        try (ZipFile zip = new ZipFile(archive(context))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            String prefix = "lib/" + abi() + "/";
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
                String name = entry.getName().substring(prefix.length());
                if (!name.matches("lib[A-Za-z0-9_-]+\\.so") || entry.getSize() < 0
                        || entry.getSize() > 150L * 1024 * 1024) throw new IOException("Invalid game library");
                File file = new File(nativeDirectory, name);
                if (file.length() != entry.getSize()) {
                    File pending = new File(nativeDirectory, name + ".part");
                    try (InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(pending)) {
                        byte[] bytes = new byte[32768]; int read;
                        while ((read = in.read(bytes)) != -1) out.write(bytes, 0, read);
                        out.getFD().sync();
                    }
                    if (pending.length() != entry.getSize() || !pending.setReadOnly()
                            || !pending.renameTo(file)) throw new IOException("Cannot extract " + name);
                }
                count++;
            }
            if (count == 0 || !new File(nativeDirectory, "libxul.so").isFile())
                throw new IOException("Gecko native libraries missing");
        }
        return new DexClassLoader(archive(context).getAbsolutePath(),
                context.getCodeCacheDir().getAbsolutePath(), nativeDirectory.getAbsolutePath(),
                context.getClassLoader());
    }
}
