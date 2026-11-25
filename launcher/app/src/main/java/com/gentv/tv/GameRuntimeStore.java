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

    static File directory(Context context) {
        return new File(context.getFilesDir(), "game-runtime/" + BuildConfig.VERSION_CODE + "/" + abi());
    }

    static File archive(Context context) {
        return new File(directory(context), "game-" + abi().replace('-', '_') + ".apk");
    }

    static boolean ready(Context context) {
        return abi() != null && archive(context).length() > 0
                && new File(directory(context), "game_ready").isFile();
    }

    static void stage(Context context, File downloadedArchive) throws IOException {
        File directory = directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create game directory");
        File ready = new File(directory, "game_ready");
        if (ready.exists() && !ready.delete()) throw new IOException("Cannot replace game component");
        copyAtomically(downloadedArchive, archive(context));
        try (FileOutputStream out = new FileOutputStream(ready)) {
            out.write(1);
            out.getFD().sync();
        }
    }

    private static void copyAtomically(File source, File destination) throws IOException {
        File pending = new File(destination.getPath() + ".part");
        if (pending.exists() && !pending.delete()) throw new IOException("Cannot replace pending game archive");
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(pending)) {
            byte[] bytes = new byte[32768];
            int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            out.getFD().sync();
        }
        if (!pending.setReadOnly() || !pending.renameTo(destination))
            throw new IOException("Cannot stage game archive " + destination.getName());
    }

    static DexClassLoader loader(Context context) throws IOException {
        if (!ready(context)) throw new IOException("Game archive missing");
        File nativeDirectory = new File(context.getCodeCacheDir(),
                "game-runtime-libs/" + BuildConfig.VERSION_CODE + "/" + abi());
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
