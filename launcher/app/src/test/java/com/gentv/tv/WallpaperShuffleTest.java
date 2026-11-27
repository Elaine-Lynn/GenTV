package com.gentv.tv;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class WallpaperShuffleTest {
    private static final long MINUTE = 60_000L;

    @Test public void picturesChangeEveryHalfHourAndVideosDaily() {
        long now = 1_800_000_000_000L;
        assertFalse(WallpaperShuffle.due(false, now - 29 * MINUTE, now));
        assertTrue(WallpaperShuffle.due(false, now - 30 * MINUTE, now));
        assertFalse(WallpaperShuffle.due(true, now - 23 * 60 * MINUTE, now));
        assertTrue(WallpaperShuffle.due(true, now - 24 * 60 * MINUTE, now));
        assertTrue(WallpaperShuffle.due(false, 0, now));
        // A clock set back must not freeze the rotation.
        assertTrue(WallpaperShuffle.due(true, now + MINUTE, now));
    }

    @Test public void aRoundPlaysEveryWallpaperOnceAndAvoidsRepeatingTheLastOne() {
        List<String> ids = Arrays.asList("a", "b", "c", "d", "e");
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            List<String> round = WallpaperShuffle.shuffled(ids, random, "c");
            assertEquals(new HashSet<>(ids), new HashSet<>(round));
            assertEquals(ids.size(), round.size());
            assertNotEquals("c", round.get(0));
        }
        assertEquals(Arrays.asList("only"), WallpaperShuffle.shuffled(Arrays.asList("only"), random, "only"));
    }

    @Test public void onlyWallpapersOnTheTvAreSwitchedTo() {
        List<String> queue = Arrays.asList("a", "b", "c", "d");
        Set<String> onTv = new HashSet<>(Arrays.asList("a", "d"));
        assertEquals(3, WallpaperShuffle.nextReady(queue, 0, onTv::contains));
        assertEquals(0, WallpaperShuffle.nextReady(queue, -1, onTv::contains));
        assertEquals(-1, WallpaperShuffle.nextReady(queue, 3, onTv::contains));
        assertEquals(1, WallpaperShuffle.readyAhead(queue, 0, onTv::contains));
        assertEquals(2, WallpaperShuffle.readyAhead(queue, -1, onTv::contains));
    }

    @Test public void downloadsBeyondTheLimitGoLeastRecentlyShownFirst() {
        Map<String, Long> used = new HashMap<>();
        Set<String> owned = new HashSet<>();
        for (int i = 0; i < 10; i++) { owned.add("w" + i); used.put("w" + i, (long) i); }
        Set<String> protect = new HashSet<>(Arrays.asList("w0", "w9"));
        List<String> gone = WallpaperShuffle.evictions(used, owned, protect, 8);
        assertEquals(Arrays.asList("w1", "w2"), gone);
        assertTrue(WallpaperShuffle.evictions(used, owned, protect, 10).isEmpty());
    }
}
