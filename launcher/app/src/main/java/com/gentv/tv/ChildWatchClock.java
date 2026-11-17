package com.gentv.tv;

/** One viewing budget shared by all selected apps, using monotonic timestamps. */
final class ChildWatchClock {
    private long last;
    private long elapsed;

    ChildWatchClock(long now, long saved) {
        last = now;
        elapsed = Math.max(0, saved);
    }

    void advance(long now, boolean counting) {
        if (counting) elapsed += Math.min(1000, Math.max(0, now - last));
        last = now;
    }

    long elapsed() {
        return elapsed;
    }

    void reset(long now) {
        elapsed = 0;
        last = now;
    }
}
