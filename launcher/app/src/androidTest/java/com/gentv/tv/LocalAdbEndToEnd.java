package com.gentv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete release UI flow, restricted to an isolated package on an emulator. */
public final class LocalAdbEndToEnd extends Instrumentation {
    private int checks;
    private int manualPort;
    private String testPin;
    private boolean compatibility;
    private boolean systemFallback;
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        manualPort = Integer.parseInt(arguments.getString("port", "0"));
        compatibility = "true".equals(arguments.getString("compat", "false"));
        systemFallback = "true".equals(arguments.getString("allowSystemFallback", "false"));
        start();
    }
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++; report.append("PASS: ").append(message).append('\n');
        Bundle progress = new Bundle(); progress.putString("stream", "PASS: " + message + "\n");sendStatus(0, progress);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity settings = null;
        try {
            String hardware = android.os.Build.HARDWARE;
            check(hardware.contains("ranchu") || hardware.contains("goldfish"), "emulator required");
            check(getTargetContext().getPackageName().endsWith(".localadbqa"), "isolated QA package required");
            SharedPreferences state = ChildLock.state(getTargetContext());
            check(state.edit().clear().putBoolean(ChildSettingsActivity.TEST_INTERVAL, true)
                    .putInt(ChildSettingsActivity.MODE, 0).putInt(ChildGrade.KEY, 3).commit(), "fresh isolated 30-second test state");
            check(ChildApps.save(state, Collections.singleton("com.gentv.childfixture")), "local video fixture selected");
            settings = startActivitySync(new Intent(getTargetContext(), ChildSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity page = settings;
            onMain(() -> {
                AlertDialog password = (AlertDialog) field(page, "passwordDialog");
                List<EditText> inputs = new ArrayList<>(); collectInputs(password.getWindow().getDecorView(), inputs);
                check(inputs.size() == 2, "parent PIN setup shown");
                testPin = Integer.toString(100000 + new java.security.SecureRandom().nextInt(900000));
                for (EditText input : inputs) input.setText(testPin);
                password.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(Boolean.TRUE.equals(field(page, "authorized")), "parent setup unlocks settings through actual UI");
                Button mode = (Button) field(page, "enableButton");
                mode.performClick();
                check(state.getBoolean(ChildSettingsActivity.ENABLED, false), "mode enables without accessibility service");
                mode.performClick();
                check(!state.getBoolean(ChildSettingsActivity.ENABLED, true), "mode disables normally");
                Button service = (Button) field(page, "permissionButton");
                check(service.getText().toString().equals(page.getString(R.string.child_adb_button_enable)), "service button initially offers activation");
                service.performClick();
            });
            waitForActivation(page);
            check(ChildWatchService.isConnected(), systemFallback
                    ? "timer service connects with explicit system-authorisation fallback when local ADB is unavailable"
                    : "actual button performs local ADB activation and binds service");
            SystemClock.sleep(1200);
            onMain(() -> {
                if (!Boolean.TRUE.equals(field(page, "authorized"))) {
                    AlertDialog password = (AlertDialog) field(page, "passwordDialog");
                    List<EditText> inputs = new ArrayList<>();collectInputs(password.getWindow().getDecorView(), inputs);
                    for (EditText input : inputs) input.setText(testPin);
                    password.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    check(Boolean.TRUE.equals(field(page, "authorized")), "parent re-authentication after system authorisation");
                }
                Button service = (Button) field(page, "permissionButton");
                check(service.getText().toString().equals(page.getString(R.string.child_adb_button_enabled))
                        && !service.isEnabled(), "button changes to enabled only after service connects");
                check(((TextView) field(page, "protectionStatus")).getText().toString()
                        .equals(page.getString(R.string.child_service_ready)), "service readiness is distinct from mode activation");
                ((Button) field(page, "enableButton")).performClick();
                check(state.getBoolean(ChildSettingsActivity.ENABLED, false), "mode enables with service connected");
                check(((TextView) field(page, "protectionStatus")).getText().toString()
                        .equals(page.getString(R.string.child_service_active)), "active protection status shown");
            });
            if (compatibility) {
                check(controller("read").contains("com.gentv.childfixture/.Observer")
                        || controller("read").contains("com.gentv.childfixture/com.gentv.childfixture.Observer"),
                        "activation preserves pre-existing accessibility service");
                verifyVisibleControl(page, "permissionButton", "service activation control is fully reachable by remote focus");
                capture("service-enabled");
                verifyVisibleControl(page, "enableButton", "mode switch is fully reachable by remote focus");
                capture("mode-control-accessible");
            }
            if (compatibility && !systemFallback) {
                java.lang.reflect.Method keyMethod = ChildLocalAdb.class.getDeclaredMethod("key", android.content.Context.class);
                keyMethod.setAccessible(true);
                java.security.KeyPair key = (java.security.KeyPair) keyMethod.invoke(null, getTargetContext());
                try (LocalAdbConnection connection = new LocalAdbConnection()) {
                    java.util.concurrent.atomic.AtomicReference<Throwable> connectionError = new java.util.concurrent.atomic.AtomicReference<>();
                    Thread connect = new Thread(() -> {
                        try { connection.connect(manualPort, key); }
                        catch (Throwable error) { connectionError.set(error); }
                    });
                    connect.start();
                    while (connect.isAlive()) { allowEmulatorDebugging(); SystemClock.sleep(150); }
                    connect.join();
                    if (connectionError.get() != null) throw new AssertionError(connectionError.get());
                    for (int stream = 0; stream < 30; stream++) {
                        check("GENTV_PROBE".equals(connection.shell("echo GENTV_PROBE")),
                                "consecutive local ADB shell stream " + stream);
                    }
                }
            }
            ActivityMonitor monitor = addMonitor(ChildChallengeActivity.class.getName(), null, false);
            getTargetContext().startActivity(new Intent().setClassName("com.gentv.childfixture", "com.gentv.childfixture.Player")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (int round = 0; round < 2; round++) {
                long began = SystemClock.elapsedRealtime();
                long excludedPause = 0;
                if (round == 0) {
                    SystemClock.sleep(20_000);
                    check(state.getInt(ChildLock.STAGE, 0) == 0, "no premature question after 20 seconds");
                    long watched = state.getLong(ChildWatchService.WATCHED, 0);
                    check(watched >= 10_000 && watched < 30_000, "foreground viewing progress checkpoints before deadline");
                    if (compatibility) {
                        long paused = SystemClock.elapsedRealtime();
                        getTargetContext().startActivity(new Intent().setClassName("com.gentv.unmanagedfixture", "com.gentv.childfixture.Player")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        SystemClock.sleep(1200);
                        long saved = state.getLong(ChildWatchService.WATCHED, 0);
                        SystemClock.sleep(5000);
                        check(state.getLong(ChildWatchService.WATCHED, 0) == saved, "watch timer pauses in an unselected app");
                        check(state.getInt(ChildLock.STAGE, 0) == 0, "no question while an unselected app is foreground");
                        getTargetContext().startActivity(new Intent().setClassName("com.gentv.childfixture", "com.gentv.childfixture.Player")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        excludedPause = SystemClock.elapsedRealtime() - paused;
                    }
                }
                Activity challenge = monitor.waitForActivityWithTimeout(round == 0 ? 18_000 : 38_000);
                check(challenge != null, "30-second viewing automatically opens question, round " + (round + 1));
                long elapsed = SystemClock.elapsedRealtime() - began;
                long activeElapsed = elapsed - excludedPause;
                check(activeElapsed >= 27_000 && activeElapsed <= 38_000,
                        "question appears near 30-second deadline (active " + activeElapsed + " ms; wall " + elapsed + " ms)");
                check(state.getInt(ChildLock.STAGE, 0) == ChildLock.QUESTION, "persisted enforced question stage");
                SystemClock.sleep(500);
                org.json.JSONArray snapshot = new org.json.JSONArray(state.getString("question_snapshot", ""));
                String answer = snapshot.getString(snapshot.getInt(1) + 2);
                if (compatibility) {
                    onMain(() -> {
                        ChildLock lock = (ChildLock) field(challenge, "lock");
                        Dialog dialog = (Dialog) field(lock, "dialog");
                        View choice = exactText(dialog.getWindow().getDecorView(), answer);
                        check(choice != null && choice.requestFocus(), "answer can receive remote focus");
                    });
                    SystemClock.sleep(400);
                    onMain(() -> {
                        Dialog dialog = (Dialog) field(field(challenge, "lock"), "dialog");
                        View choice = exactText(dialog.getWindow().getDecorView(), answer);
                        android.graphics.Rect visible = new android.graphics.Rect();
                        check(choice != null && choice.getGlobalVisibleRect(visible)
                                && visible.height() >= choice.getHeight(), "focused answer scrolls fully into view");
                    });
                    capture("question-round-" + (round + 1));
                }
                onMain(() -> {
                    ChildLock lock = (ChildLock) field(challenge, "lock");
                    Dialog dialog = (Dialog) field(lock, "dialog");
                    View choice = exactText(dialog.getWindow().getDecorView(), answer);
                    check(choice != null && choice.performClick(), "correct answer accepted through question UI");
                });
                check(state.getInt(ChildLock.STAGE, 0) == 0, "correct answer releases viewing restriction");
                SystemClock.sleep(1500);
                check(challenge.isFinishing() || challenge.isDestroyed(), "question closes and player can resume");
                if (round == 0) {
                    // A monitor can retain the previous activity's final resume notification.
                    removeMonitor(monitor);
                    monitor = addMonitor(ChildChallengeActivity.class.getName(), null, false);
                }
            }
            check(ChildWatchService.isConnected(), "timer service remains connected after repeated questions");
            removeMonitor(monitor);
            if (compatibility) verifyRevocation(state, page);
            result.putString("stream", report + "TOTAL: " + checks + " checks passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", report + "FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void waitForActivation(Activity page) throws Exception {
        long start = SystemClock.elapsedRealtime();
        while (!ChildWatchService.isConnected() && SystemClock.elapsedRealtime() - start < (systemFallback ? 6000 : 100_000)) {
            onMain(() -> {
                AlertDialog dialog = (AlertDialog) field(page, "serviceDialog");
                if (dialog != null && dialog.isShowing()) {
                    List<EditText> inputs = new ArrayList<>();collectInputs(dialog.getWindow().getDecorView(), inputs);
                    if (inputs.size() == 1 && manualPort > 0) {
                        inputs.get(0).setText(Integer.toString(manualPort));
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    }
                }
            });
            allowEmulatorDebugging();
            SystemClock.sleep(300);
        }
        if (!ChildWatchService.isConnected() && systemFallback) {
            Bundle capability = new Bundle();
            capability.putString("stream", "CAPABILITY: local_adb=unavailable; using test-harness system authorisation\n");
            sendStatus(0, capability);
            onMain(() -> {
                AlertDialog dialog = (AlertDialog) field(page, "serviceDialog");
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            });
            check(controller("enable").contains(getTargetContext().getPackageName() + "/"),
                    "test harness explicitly grants system accessibility permission without claiming self-ADB success");
            for (int i = 0; i < 40 && !ChildWatchService.isConnected(); i++) SystemClock.sleep(200);
        }
    }
    private void verifyRevocation(SharedPreferences state, Activity page) throws Exception {
        // Reuse the stopped settings instance without startActivitySync's idle wait on the PIN dialog.
        getTargetContext().startActivity(new Intent(getTargetContext(), ChildSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        java.util.concurrent.atomic.AtomicBoolean resumed = new java.util.concurrent.atomic.AtomicBoolean();
        for (int i = 0; i < 40; i++) {
            onMain(() -> resumed.set(Boolean.TRUE.equals(field(page, "resumed"))));
            if (resumed.get()) break;
            SystemClock.sleep(200);
        }
        check(resumed.get(), "protected settings resumes for revocation test");
        authenticateExisting(page);
        String before = controller("read");
        List<String> others = new ArrayList<>();
        for (String component : before.split(":")) {
            if (!component.startsWith(getTargetContext().getPackageName() + "/")) others.add(component);
        }
        String without = String.join(":", others);
        check(without.contains("com.gentv.childfixture/"), "revocation test retains independent observer service");
        String revoked = controller("disable");
        check(!revoked.contains(getTargetContext().getPackageName() + "/"), "fault injector removed only the isolated service entry");
        long began = SystemClock.elapsedRealtime();
        while (ChildWatchService.isConnected() && SystemClock.elapsedRealtime() - began < 8000) SystemClock.sleep(200);
        check(!ChildWatchService.isConnected(), "service disconnects after permission is revoked");
        SystemClock.sleep(1300);
        onMain(() -> {
            Button button = (Button) field(page, "permissionButton");
            check(button.isEnabled() && button.getText().toString().equals(page.getString(R.string.child_adb_button_enable)),
                    "button returns to activation state after service is revoked");
            check(((TextView) field(page, "protectionStatus")).getText().toString()
                    .equals(page.getString(R.string.child_service_disconnected)), "revoked service does not claim protection is active");
            check(state.getBoolean(ChildSettingsActivity.ENABLED, false), "mode preference survives service revocation");
            button.performClick();
        });
        waitForActivation(page);
        check(ChildWatchService.isConnected(), systemFallback
                ? "service reconnects after renewed system authorisation"
                : "real button reconnects service after revocation");
        SystemClock.sleep(1300);
        authenticateExisting(page);
        onMain(() -> check(((Button) field(page, "permissionButton")).getText().toString()
                .equals(page.getString(R.string.child_adb_button_enabled)), "button confirms re-enabled service"));
        check(controller("read").contains("com.gentv.childfixture/"),
                "re-enabling preserves other accessibility services");
        capture("service-restored");
    }
    private void authenticateExisting(Activity page) {
        onMain(() -> {
            if (Boolean.TRUE.equals(field(page, "authorized"))) return;
            AlertDialog password = (AlertDialog) field(page, "passwordDialog");
            List<EditText> inputs = new ArrayList<>();collectInputs(password.getWindow().getDecorView(), inputs);
            for (EditText input : inputs) input.setText(testPin);
            password.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            check(Boolean.TRUE.equals(field(page, "authorized")), "existing parent PIN unlocks protected settings");
        });
    }
    private String controller(String operation) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> value = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger code = new java.util.concurrent.atomic.AtomicInteger();
        android.content.BroadcastReceiver callback = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                value.set(getResultData());code.set(getResultCode());done.countDown();
            }
        };
        Intent request = new Intent("com.gentv.fixture.ACCESSIBILITY")
                .setClassName("com.gentv.childfixture", "com.gentv.childfixture.Controller")
                .putExtra("operation", operation);
        getTargetContext().sendOrderedBroadcast(request, null, callback,
                new android.os.Handler(android.os.Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null);
        if (!done.await(10, java.util.concurrent.TimeUnit.SECONDS) || code.get() != Activity.RESULT_OK) {
            throw new AssertionError("Test-only permission controller did not finish: " + value.get());
        }
        return value.get() == null ? "" : value.get();
    }
    private void verifyVisibleControl(Activity page, String field, String message) throws Exception {
        setInTouchMode(false);
        java.util.concurrent.atomic.AtomicReference<View> target = new java.util.concurrent.atomic.AtomicReference<>();
        onMain(() -> {
            View control = (View) field(page, field);
            target.set(control.isEnabled() ? control : (View) field(page, "playerButton"));
        });
        if (field.equals("enableButton")) sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
        for (int step = 0; step < 35 && !target.get().hasFocus(); step++) {
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            SystemClock.sleep(120);
        }
        check(target.get().hasFocus(), "remote direction keys reach " + field);
        SystemClock.sleep(600);
        capture("focus-check-" + field);
        onMain(() -> {
            View control = (View) field(page, field);
            android.graphics.Rect visible = new android.graphics.Rect();
            check(control.getGlobalVisibleRect(visible) && visible.height() >= control.getHeight()
                    && visible.width() >= control.getWidth(), message + " [visible=" + visible.width() + "x" + visible.height()
                            + ", control=" + control.getWidth() + "x" + control.getHeight()
                    + ", attached=" + control.isAttachedToWindow() + ", shown=" + control.isShown()
                    + ", focused=" + (control.isEnabled() ? control : (View) field(page, "playerButton")).hasFocus()
                    + ", tree=" + ancestors(control) + ", windowFocus=" + control.hasWindowFocus() + ", touch=" + control.isInTouchMode() + "]");
        });
    }
    private static String ancestors(View view) {
        StringBuilder result = new StringBuilder();
        while (view != null) {
            result.append(view.getClass().getSimpleName()).append(":top=").append(view.getTop())
                    .append(",h=").append(view.getHeight()).append(",scroll=").append(view.getScrollY()).append(";");
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return result.toString();
    }
    private void capture(String name) throws Exception {
        android.graphics.Bitmap bitmap = getUiAutomation(1).takeScreenshot();
        check(bitmap != null, "captured " + name);
        java.io.File directory = getTargetContext().getExternalFilesDir("compat");
        if (directory == null || !directory.exists() && !directory.mkdirs()) throw new java.io.IOException("Screenshot directory unavailable");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);output.getFD().sync();
        } finally { bitmap.recycle(); }
    }
    private void onMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private void allowEmulatorDebugging() {
        // Only this isolated emulator test auto-accepts its own generated test key.
        AccessibilityNodeInfo root = getUiAutomation(1).getRootInActiveWindow();
        if (root == null) return;
        try {
            CharSequence pkg = root.getPackageName();
            if (pkg == null || !pkg.toString().equals("com.android.systemui")) return;
            List<AccessibilityNodeInfo> remember = root.findAccessibilityNodeInfosByViewId("android:id/checkbox");
            for (AccessibilityNodeInfo node : remember) {
                if (node.isCheckable() && !node.isChecked()) node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                node.recycle();
            }
            List<AccessibilityNodeInfo> allow = root.findAccessibilityNodeInfosByViewId("android:id/button1");
            for (AccessibilityNodeInfo node : allow) {
                CharSequence label = node.getText();
                if (label != null && (label.toString().equalsIgnoreCase("Allow") || label.toString().equals("允许"))) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                node.recycle();
            }
        } finally { root.recycle(); }
    }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void collectInputs(View view,List<EditText> inputs) {
        if(view instanceof EditText input)inputs.add(input);
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collectInputs(group.getChildAt(i),inputs);
    }
    private static View exactText(View view,String label) {
        if(view instanceof TextView text && text.getText().toString().equals(label) && view.isClickable())return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=exactText(group.getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
}
