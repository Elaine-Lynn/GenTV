package com.gentv.tv;

/** Home-only idle deadline, using monotonic time; input or returning to Home starts a full interval. */
final class ScreensaverIdle {
    static final String MINUTES_KEY = "screensaver_minutes";
    static final int DEFAULT_MINUTES = 2;
    private long deadline;
    private boolean active;

    static int minutes(int value) {
        return value == 0 || value == 2 || value == 10 || value == 60 ? value : DEFAULT_MINUTES;
    }

    void restart(long now, int minutes) {
        int delay = minutes(minutes);
        active = delay > 0;
        deadline = now + delay * 60_000L;
    }

    void pause() { active = false; }
    boolean due(long now) { return active && now >= deadline; }
    long remaining(long now) { return active ? Math.max(0, deadline - now) : -1; }
}
