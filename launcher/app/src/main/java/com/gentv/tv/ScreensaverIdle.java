package com.gentv.tv;

/** Home-only idle deadline, using monotonic time; input or returning to Home starts a full interval. */
final class ScreensaverIdle {
    static final String MINUTES_KEY = "screensaver_minutes";
    static final int DEFAULT_MINUTES = 2;
    /** What the screensaver shows (with MINUTES_KEY 0 meaning off): the aerial videos, or the wallpaper itself. */
    static final String STYLE_KEY = "screensaver_style";
    static final String STYLE_AERIAL = "aerial";
    static final String STYLE_WALLPAPER = "wallpaper";
    private long deadline;
    private boolean active;

    static int minutes(int value) {
        return value == 0 || value == 2 || value == 10 || value == 60 ? value : DEFAULT_MINUTES;
    }

    /** The aerial videos unless the wallpaper was chosen; anything unknown is the default. */
    static String style(String value) {
        return STYLE_WALLPAPER.equals(value) ? STYLE_WALLPAPER : STYLE_AERIAL;
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
