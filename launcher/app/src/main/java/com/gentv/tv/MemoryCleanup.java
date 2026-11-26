package com.gentv.tv;

import android.app.ActivityManager;
import android.content.Context;
import java.util.HashSet;
import java.util.Set;

/** One user-initiated cleanup using Android's own safe-to-kill background process policy. */
final class MemoryCleanup {
    interface Callback { void finished(boolean success, long freedMb, int exitedProcesses); }

    static final class Task {
        volatile boolean cancelled;
        final LocalAdbConnection connection = new LocalAdbConnection();
        void cancel() {
            cancelled = true;
            try { connection.close(); } catch (java.io.IOException ignored) { }
        }
    }

    static Task start(Context context, int port, Callback callback) {
        Task task = new Task();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String phase = "connect";
            try (LocalAdbConnection adb = task.connection) {
                int actualPort = ChildLocalAdb.reportedPort();
                if (actualPort == 0) actualPort = port;
                if (actualPort < 1 || actualPort > 65535) throw new java.io.IOException("ADB unavailable");
                adb.connect(actualPort, LocalAdbIdentity.key(app));
                phase = "identity";
                if (!"2000".equals(adb.shell("id -u"))) throw new java.io.IOException("Not an ADB shell");
                phase = "snapshot";
                Set<String> before = appProcesses(adb.shell("ps -A -o USER,PID,NAME"));
                long memoryBefore = availableMemory(app);
                if (task.cancelled) return;
                phase = "kill";
                String result = adb.shell("am kill-all && echo GENTV_MEMORY_KILL_OK");
                if (!"GENTV_MEMORY_KILL_OK".equals(result)) throw new java.io.IOException("Cleanup refused");
                Thread.sleep(900); // Let process exits and memory accounting settle.
                phase = "verify";
                Set<String> after = appProcesses(adb.shell("ps -A -o USER,PID,NAME"));
                before.removeAll(after);
                long freed = reclaimedMb(memoryBefore, availableMemory(app), before.size());
                if (!task.cancelled) callback.finished(true, freed, before.size());
            } catch (Exception failure) {
                // No shell output, keys or authentication material enter logs or UI.
                android.util.Log.w("GenTVMemory", phase + ": " + failure.getClass().getSimpleName());
                if (!task.cancelled) callback.finished(false, 0, 0);
            }
        }, "gentv-background-cleanup").start();
        return task;
    }

    private static long availableMemory(Context app) {
        ActivityManager manager = CompatApi.service(app, ActivityManager.class);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return info.availMem;
    }

    static long reclaimedMb(long before, long after, int exitedProcesses) {
        // Idle memory fluctuation without an observed application exit is not a cleanup result.
        return exitedProcesses > 0 ? Math.max(0, after - before) / (1024L * 1024L) : 0;
    }

    /** PID + process name avoids counting a reused PID as an unchanged process. */
    static Set<String> appProcesses(String output) {
        Set<String> result = new HashSet<>();
        for (String line : output.split("\\r?\\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length == 3 && fields[0].matches("u[0-9]+_a[0-9]+")
                    && fields[1].matches("[0-9]+")) result.add(fields[1] + ":" + fields[2]);
        }
        return result;
    }
}
