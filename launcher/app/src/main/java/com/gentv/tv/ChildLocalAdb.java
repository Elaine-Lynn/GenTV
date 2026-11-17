package com.gentv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.concurrent.TimeUnit;

/** User-initiated, one-shot local authorisation. Never starts adbd or grants broad app permissions. */
final class ChildLocalAdb {
    private static volatile boolean busy;
    interface Callback { void finished(boolean connected, boolean restored); }

    static synchronized boolean start(Context context, int port, Callback callback) {
        if (busy) return false;
        busy = true;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean connected = false, restored = false;
            String before = null, global = null, updated = null;
            String phase = "connect";
            try (LocalAdbConnection adb = new LocalAdbConnection()) {
                adb.connect(port, key(app));
                phase = "read-services";
                before = adb.shell("settings get secure enabled_accessibility_services");
                phase = "read-enabled";
                global = adb.shell("settings get secure accessibility_enabled");
                updated = ChildServiceSettings.add(before, app.getPackageName());
                SharedPreferences recovery = app.getSharedPreferences("child_adb_recovery", Context.MODE_PRIVATE);
                if (!recovery.edit().putString("services_before", before).putString("enabled_before", global)
                        .putString("services_after", updated).putBoolean("pending", true).commit()) {
                    throw new IOException("Cannot save recovery baseline");
                }
                phase = "enable";
                String result = adb.shell(ChildServiceSettings.enable(before, global, updated));
                if (!result.contains("GENTV_CHILD_ENABLED")) throw new IOException("Settings changed or write refused");
                phase = "wait-for-service";
                for (int i = 0; i < 60 && !ChildWatchService.isConnected(); i++) Thread.sleep(200);
                connected = ChildWatchService.isConnected();
                if (!connected) {
                    phase = "restore";
                    restored = adb.shell(ChildServiceSettings.rollback(before, global, updated))
                            .contains("GENTV_CHILD_RESTORED");
                }
                recovery.edit().putBoolean("pending", !connected && !restored).commit();
            } catch (Exception failure) {
                // Do not print tokens, keys, or settings. Preserve recovery baseline if a write was uncertain.
                android.util.Log.w("GenTVChildAdb", "Local activation at " + phase + ": " + failure.getClass().getSimpleName()
                        + (failure instanceof LocalAdbConnection.ProtocolFailure ? " (" + failure.getMessage() + ")" : ""));
                if (before != null && global != null && updated != null) {
                    // A lost connection may have completed its write: use the actual local service state.
                    connected = ChildWatchService.isConnected();
                }
            } finally {
                busy = false;
            }
            callback.finished(connected, restored);
        }, "GenTVChildLocalAdb").start();
        return true;
    }

    static boolean busy() { return busy; }

    /** Only a reported legacy TCP port is prefilled; no port scanning or inferred default. */
    static int reportedPort() {
        for (String property : new String[]{"service.adb.tcp.port", "persist.adb.tcp.port"}) {
            Process process = null;
            try {
                process = new ProcessBuilder("/system/bin/getprop", property).start();
                if (!process.waitFor(2, TimeUnit.SECONDS)) continue;
                byte[] bytes = new byte[32];
                int count = process.getInputStream().read(bytes);
                if (count <= 0) continue;
                int port = Integer.parseInt(new String(bytes, 0, count, java.nio.charset.StandardCharsets.UTF_8).trim());
                if (port >= 1 && port <= 65535) return port;
            } catch (IOException | InterruptedException | NumberFormatException ignored) {
                // A user can enter the actual port shown in TV settings instead.
            } finally { if (process != null) process.destroy(); }
        }
        return 0;
    }

    private static synchronized KeyPair key(Context context) throws Exception {
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), "child-local-adb.pk8"));
        if (file.getBaseFile().exists()) {
            byte[] encoded = file.readFully();
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
            return new KeyPair(factory.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(),
                    privateKey.getPublicExponent())), privateKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        FileOutputStream output = file.startWrite();
        try {
            output.write(pair.getPrivate().getEncoded());
            file.finishWrite(output);
        } catch (IOException failure) { file.failWrite(output); throw failure; }
        return pair;
    }
}
