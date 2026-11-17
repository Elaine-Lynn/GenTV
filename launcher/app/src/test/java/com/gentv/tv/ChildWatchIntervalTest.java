package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ChildWatchIntervalTest {
    @Test public void retiredTestUsesTenMinutesRegardlessOfPreviousNormalInterval() {
        for (int stored : new int[]{0, 5, 10, 15, 20}) {
            assertEquals(10, ChildWatchInterval.minutes(stored, true));
        }
    }

    @Test public void preservesSupportedNormalIntervals() {
        for (int stored : new int[]{5, 10, 15, 20}) {
            assertEquals(stored, ChildWatchInterval.minutes(stored, false));
        }
    }

    @Test public void unsupportedStoredIntervalsFallBackToTenMinutes() {
        for (int stored : new int[]{-1, 0, 1, 30, Integer.MAX_VALUE}) {
            assertEquals(10, ChildWatchInterval.minutes(stored, false));
        }
    }
}
