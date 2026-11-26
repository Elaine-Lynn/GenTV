package com.gentv.tv;

import org.junit.Test;

import static org.junit.Assert.*;

public class LocationRetryPolicyTest {
    private static final long START = 1_000_000L;

    @Test public void spacesAttemptsFiveMinutesApart() {
        assertEquals(LocationRetryPolicy.RETRY_MS,
                LocationRetryPolicy.delay(START, START, 1, START));
        assertEquals(0, LocationRetryPolicy.delay(START + LocationRetryPolicy.RETRY_MS,
                START, 1, START));
    }

    @Test public void capsAttemptsUntilNextWindow() {
        long thirdAttempt = START + 2 * LocationRetryPolicy.RETRY_MS;
        assertEquals(LocationRetryPolicy.WINDOW_MS - 2 * LocationRetryPolicy.RETRY_MS,
                LocationRetryPolicy.delay(thirdAttempt, START, 3, thirdAttempt));
        assertEquals(0, LocationRetryPolicy.delay(START + LocationRetryPolicy.WINDOW_MS,
                START, 3, thirdAttempt));
        assertTrue(LocationRetryPolicy.newWindow(START + LocationRetryPolicy.WINDOW_MS, START));
    }

    @Test public void clockRollbackStartsFreshWindow() {
        assertTrue(LocationRetryPolicy.newWindow(START - 1, START));
        assertEquals(0, LocationRetryPolicy.delay(START - 1, START, 3, START));
    }
}
