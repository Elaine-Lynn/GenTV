package com.gentv.tv;

/** Monotonic foreground deltas; no wall-clock time or background duration. */
final class MetricsClock {
    private long tick = -1, remainder;
    void start(long now) { tick = now; remainder = 0; }
    int sample(long now) {
        if (tick < 0) return 0;
        long delta = Math.max(0, now - tick) + remainder;
        tick = now; remainder = delta % 1000;
        return (int)Math.min(120, delta / 1000);
    }
    void stop() { tick = -1; remainder = 0; }
}
