package com.gentv.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real on-device loopback/RSA proof in an isolated QA package, never the user's production preferences. */
public final class LocalAdbIntegration extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!getTargetContext().getPackageName().endsWith(".localadbqa")) {
                throw new AssertionError("Isolated test package required");
            }
            int port = ChildLocalAdb.reportedPort();
            if (port == 0) throw new AssertionError("Device must report its actual legacy ADB port");
            startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean connected = new AtomicBoolean();
            if (!ChildLocalAdb.start(getTargetContext(), port, (active, restored) -> {
                connected.set(active); done.countDown();
            })) throw new AssertionError("Activation already running");
            if (!done.await(115, TimeUnit.SECONDS)) throw new AssertionError("Activation timed out");
            if (!connected.get() || !ChildWatchService.isConnected()) throw new AssertionError("Service did not bind");
            result.putString("stream", "PASS: TV app connected to its own reported loopback ADB port, authenticated with its own key, and bound ChildWatchService.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
