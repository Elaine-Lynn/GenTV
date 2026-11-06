package com.gentv.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ChildAppsTest {
    @Test public void singleSelectionMigratesAndMultipleSelectionsTakePrecedence() {
        assertEquals(Set.of("video.old"), ChildApps.resolve(null, "video.old"));
        assertEquals(Set.of("video.a", "video.b"),
                ChildApps.resolve(Set.of("video.a", "video.b"), "video.old"));
        assertTrue(ChildApps.resolve(Set.of(), "video.old").isEmpty());
    }

    @Test public void selectionDoesNotDependOnInstalledPackagesOrAliasReturnedByCaller() {
        Set<String> saved = new HashSet<>(Set.of("video.uninstalled", "video.installed"));
        Set<String> selected = ChildApps.resolve(saved, "");
        saved.clear();
        assertEquals(Set.of("video.uninstalled", "video.installed"), selected);
    }

    @Test public void switchingManagedAppsDoesNotLoseTimeBetweenTimerTicks() {
        ChildWatchClock clock = new ChildWatchClock(0, 0);
        clock.advance(200, true);
        clock.advance(350, true);
        clock.advance(500, true);
        assertEquals(500, clock.elapsed());
        clock.advance(900, false);
        clock.advance(1100, true);
        assertEquals(700, clock.elapsed());
    }

    @Test public void interruptionDoesNotCountUnboundedBackgroundTime() {
        ChildWatchClock clock = new ChildWatchClock(100, 500);
        clock.advance(10000, false);
        clock.advance(10500, true);
        assertEquals(1000, clock.elapsed());
        clock.reset(10500);
        assertEquals(0, clock.elapsed());
    }
}
