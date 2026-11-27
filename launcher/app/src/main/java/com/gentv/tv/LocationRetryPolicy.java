package com.gentv.tv;

/** Limits automatic city lookup to three attempts per rolling 24-hour window. */
final class LocationRetryPolicy {
    static final int MAX_ATTEMPTS = 3;
    static final long RETRY_MS = 5 * 60_000L;
    static final long WINDOW_MS = 24 * 60 * 60_000L;

    private LocationRetryPolicy() { }

    static long delay(long now, long windowStart, int attempts, long lastAttempt) {
        if (windowStart > 0 && now >= windowStart && now - windowStart < WINDOW_MS
                && attempts >= MAX_ATTEMPTS) return WINDOW_MS - (now - windowStart);
        long elapsed = now - lastAttempt;
        return lastAttempt > 0 && elapsed >= 0 && elapsed < RETRY_MS ? RETRY_MS - elapsed : 0;
    }

    static boolean newWindow(long now, long windowStart) {
        return windowStart <= 0 || now < windowStart || now - windowStart >= WINDOW_MS;
    }
}
