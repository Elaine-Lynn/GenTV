package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverIdleTest {
    @Test public void eachSettingWaitsTheWholeInterval() {
        for (int minutes : new int[]{2, 10, 60}) {
            ScreensaverIdle idle = new ScreensaverIdle();
            idle.restart(1000, minutes);
            long deadline = 1000 + minutes * 60_000L;
            assertFalse(idle.due(deadline - 1));
            assertTrue(idle.due(deadline));
            assertEquals(0, idle.remaining(deadline + 10));
        }
    }
    @Test public void inputRestartsTheFullInterval() {
        ScreensaverIdle idle = new ScreensaverIdle();
        idle.restart(0, 2);
        idle.restart(119_000, 2);
        assertFalse(idle.due(120_000));
        assertFalse(idle.due(238_999));
        assertTrue(idle.due(239_000));
    }
    @Test public void timeOutsideHomeDoesNotTriggerScreensaver() {
        ScreensaverIdle idle = new ScreensaverIdle();
        idle.restart(0, 2);
        idle.pause();
        assertFalse(idle.due(3_600_000));
        assertEquals(-1, idle.remaining(3_600_000));
        idle.restart(3_600_000, 2);
        assertFalse(idle.due(3_719_999));
        assertTrue(idle.due(3_720_000));
    }
    @Test public void neverCancelsAnAlreadyArmedDeadline() {
        ScreensaverIdle idle = new ScreensaverIdle();
        idle.restart(0, 2);
        idle.restart(1000, 0);
        assertFalse(idle.due(1000));
        assertFalse(idle.due(Long.MAX_VALUE));
        assertEquals(-1, idle.remaining(3_600_000));
        idle.restart(3_600_000, 10);
        assertFalse(idle.due(4_199_999));
        assertTrue(idle.due(4_200_000));
    }
    @Test public void invalidStoredSettingFallsBackToTwoMinutes() {
        for (int invalid : new int[]{-1, 1, 3, 30, 120, Integer.MAX_VALUE})
            assertEquals(2, ScreensaverIdle.minutes(invalid));
    }
}
