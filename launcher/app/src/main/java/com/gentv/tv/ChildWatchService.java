package com.gentv.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Random;
import java.util.Set;
import java.util.Collections;

/** Times the configured foreground app while the screen is interactive. */
public final class ChildWatchService extends AccessibilityService {
    private static volatile ChildWatchService connected;
    static boolean isConnected() {
        return connected != null;
    }

    static final String WATCHED = "watched_ms";
    static final String PLAYBACK = "resume_player";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences state;
    private PowerManager power;
    private String foreground = "";
    private Set<String> targets = Collections.emptySet();
    private ChildWatchClock clock;
    private long lastOpen;
    private ChildLock overlay;
    private android.media.AudioFocusRequest overlayFocus;
    private boolean overlayUnavailable;
    private String overlayFallbackReason = "none";


    @Override protected void onServiceConnected() {
        connected = this;
        state = ChildLock.state(this);
        power = getSystemService(PowerManager.class);
        targets = ChildApps.read(state);
        clock = new ChildWatchClock(SystemClock.elapsedRealtime(), state.getLong(WATCHED, 0));
        if (state.getBoolean(ChildSettingsActivity.ENABLED, false)) updateActiveWindow();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (state == null || !state.getBoolean(ChildSettingsActivity.ENABLED, false)
                || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null) return;
        // Window events identify the new owner even when the cached active root still
        // belongs to the translucent challenge that just closed (seen on DBB3 Pro).
        if (!ChildLock.locked(this) && event.getClassName() != null
                && ChildChallengeActivity.class.getName().contentEquals(event.getClassName())) return;
        setForeground(event.getPackageName().toString());
    }

    private boolean updateActiveWindow() {
        for (android.view.accessibility.AccessibilityWindowInfo window : getWindows()) {
            try {
                if (!window.isFocused()) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    try {
                        CharSequence pkg = root.getPackageName();
                        if (pkg != null) {
                            setForeground(pkg.toString());
                            return true;
                        }
                    } finally { root.recycle(); }
                }
            } finally { window.recycle(); }
        }
        if (foreground.isEmpty()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    if (root.refresh() && root.getPackageName() != null) {
                        setForeground(root.getPackageName().toString());
                        return true;
                    }
                } finally { root.recycle(); }
            }
        }
        // With no focused window, preserve the latest window event. Reading the
        // cached active root here can overwrite it with a dismissed dialog's package.
        return false;
    }

    private void setForeground(String pkg) {
        if (!foreground.equals(pkg)) {
            account(SystemClock.elapsedRealtime());
            if (targets.contains(foreground)) saveProgress();
            foreground = pkg;
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            targets = ChildApps.read(state);
            boolean interactive = power.isInteractive();
            boolean enabled = state.getBoolean(ChildSettingsActivity.ENABLED, false);
            if (enabled && interactive) updateActiveWindow();
            long now = SystemClock.elapsedRealtime();
            account(now);
            if (!enabled) {
                clock.reset(now);
            } else if (targets.contains(foreground) && interactive) {
                if (ChildLock.locked(ChildWatchService.this)) {
                    if (now - lastOpen >= 1_500L) openChallenge(now);
                } else {
                    long interval = BuildConfig.CHILD_TIMER_TEST_MODE && state.getBoolean(ChildSettingsActivity.TEST_INTERVAL, false)
                            ? 30_000L : ChildWatchInterval.minutes(state.getInt(ChildSettingsActivity.MINUTES, 10), false) * 60_000L;
                    if (clock.elapsed() >= interval) startChallenge(now);
                    else if (clock.elapsed() - state.getLong(WATCHED, 0) >= 10_000L) saveProgress();
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    private void account(long now) {
        if (clock == null) return;
        boolean counting = state.getBoolean(ChildSettingsActivity.ENABLED, false)
                && power.isInteractive() && targets.contains(foreground) && !ChildLock.locked(this);
        clock.advance(now, counting);
    }

    private void startChallenge(long now) {
        int mode = state.getInt(ChildSettingsActivity.MODE, 2);
        boolean words = mode == 1 || mode == 2 && state.getInt(ChildLock.ROUND, 0) % 2 != 0;
        int level = ChildGrade.read(state);
        if (state.edit().putInt(ChildLock.STAGE, ChildLock.QUESTION).putInt(ChildLock.WRONG, 0)
                .putBoolean(ChildLock.WORDS, words).putInt(ChildLock.LEVEL, level)
                .putLong(ChildLock.SEED, new Random().nextLong()).putString(PLAYBACK, foreground)
                .remove(WATCHED).commit()) {
            clock.reset(now);
            openChallenge(now);
        }
    }

    private void openChallenge(long now) {
        lastOpen = now;
        if (overlay != null) return;
        android.app.admin.DevicePolicyManager policies = getSystemService(android.app.admin.DevicePolicyManager.class);
        boolean managedDevice = policies != null && policies.isDeviceOwnerApp(getPackageName());
        // Enable only after this exact firmware/player combination passes pause,
        // silence, remote-control and recovery checks. DBB3 Pro + Tencent did not.
        if (state.getBoolean("overlay_verified", false) && !managedDevice && !overlayUnavailable
                && state.getInt(ChildLock.STAGE, 0) == ChildLock.QUESTION) {
            try {
                android.media.AudioManager audio = getSystemService(android.media.AudioManager.class);
                overlayFocus = new android.media.AudioFocusRequest.Builder(
                        android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setOnAudioFocusChangeListener(change -> {
                            if (change < 0 && overlay != null) fallbackChallenge("audio_focus_loss:" + change);
                        }, handler).build();
                if (audio.requestAudioFocus(overlayFocus) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    fallbackChallenge("audio_focus_denied");
                    return;
                }
                audio.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
                audio.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
                overlay = new ChildLock(this, () -> fallbackChallenge("rest_or_parent_entry"));
                overlay.showIfLocked();
                return;
            } catch (android.view.WindowManager.BadTokenException | SecurityException | IllegalStateException failure) {
                android.util.Log.w("GenTVChildLock", "Overlay unavailable; using activity", failure);
                fallbackChallenge("window_error:" + failure.getClass().getSimpleName());
                return;
            }
        }
        openActivityChallenge();
    }

    private void releaseOverlay() {
        if (overlay != null) { overlay.dismiss(); overlay = null; }
        if (overlayFocus != null) {
            getSystemService(android.media.AudioManager.class).abandonAudioFocusRequest(overlayFocus);
            overlayFocus = null;
        }
    }

    private void fallbackChallenge(String reason) {
        overlayFallbackReason = reason;
        android.util.Log.i("GenTVChildLock", "Overlay fallback: " + reason);
        overlayUnavailable = true;
        // Keep the audio focus until the fallback activity has taken the foreground.
        if (overlay != null) { overlay.dismiss(); overlay = null; }
        openActivityChallenge();
        handler.postDelayed(this::releaseOverlay, 500);
    }

    private void openActivityChallenge() {
        startActivity(new Intent(this, ChildChallengeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    void overlayFinished(String player) {
        releaseOverlay();
        challengeFinished(player);
    }

    static void challengeFinished(String player) {
        ChildWatchService service = connected;
        if (service == null || player.isEmpty()) return;
        service.handler.post(() -> {
            if (service != connected || ChildLock.locked(service)) return;
            service.clock.reset(SystemClock.elapsedRealtime());
            service.setForeground(player);
        });
    }

    private void saveProgress() {
        if (state != null && state.getBoolean(ChildSettingsActivity.ENABLED, false)
                && !ChildLock.locked(this)) state.edit().putLong(WATCHED, clock.elapsed()).apply();
    }

    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        if (state == null) { writer.println("ChildWatch: not connected"); return; }
        writer.println("ChildWatch enabled=" + state.getBoolean(ChildSettingsActivity.ENABLED, false)
                + " interactive=" + power.isInteractive()
                + " foreground=" + foreground + " managed=" + targets.contains(foreground)
                + " targets=" + targets.size()
                + " watched_ms=" + clock.elapsed()
                + " interval_ms=" + (BuildConfig.CHILD_TIMER_TEST_MODE && state.getBoolean(ChildSettingsActivity.TEST_INTERVAL, false)
                    ? 30_000L : ChildWatchInterval.minutes(state.getInt(ChildSettingsActivity.MINUTES, 10), false) * 60_000L)
                + " stage=" + state.getInt(ChildLock.STAGE, 0)
                + " overlay=" + (overlay != null) + " overlay_fallback=" + overlayUnavailable + " fallback_reason=" + overlayFallbackReason);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (connected == this) connected = null;
        releaseOverlay();
        handler.removeCallbacks(tick);
        saveProgress();
        super.onDestroy();
    }
}
