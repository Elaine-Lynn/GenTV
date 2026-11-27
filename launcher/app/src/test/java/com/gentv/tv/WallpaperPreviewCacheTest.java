package com.gentv.tv;

import org.junit.Test;

import static org.junit.Assert.*;

public class WallpaperPreviewCacheTest {
    @Test public void fileNamesAreStablePerAssetPath() {
        String name = WallpaperPreviewCache.fileName("wallpapers/pet-sunlit-cat-preview.jpg");
        assertEquals(name, WallpaperPreviewCache.fileName("wallpapers/pet-sunlit-cat-preview.jpg"));
        assertTrue(name.matches("[0-9a-f]{32}\\.img"));
        assertNotEquals(name, WallpaperPreviewCache.fileName("wallpapers/pet-halloween-cat-preview.jpg"));
    }

    @Test public void savedPostersAreCheckedAtMostOnceADay() {
        long now = 1_800_000_000_000L;
        assertFalse(WallpaperPreviewCache.due(now - 60_000L, now));
        assertFalse(WallpaperPreviewCache.due(now - WallpaperPreviewCache.REVALIDATE_MS + 1, now));
        assertTrue(WallpaperPreviewCache.due(now - WallpaperPreviewCache.REVALIDATE_MS, now));
        assertTrue(WallpaperPreviewCache.due(0, now));
        // A clock set back must not keep a poster from ever being checked again.
        assertTrue(WallpaperPreviewCache.due(now + 3_600_000L, now));
    }

    @Test public void validatorsReadBackWhateverTheServerSent() {
        assertArrayEquals(new String[]{"\"abc\"", "Fri, 27 Nov 2026 07:00:00 GMT"},
                WallpaperPreviewCache.parseTags("\"abc\"\nFri, 27 Nov 2026 07:00:00 GMT"));
        assertArrayEquals(new String[]{"", "Fri, 27 Nov 2026 07:00:00 GMT"},
                WallpaperPreviewCache.parseTags("\nFri, 27 Nov 2026 07:00:00 GMT"));
        assertArrayEquals(new String[]{"", ""}, WallpaperPreviewCache.parseTags(""));
        assertArrayEquals(new String[]{"", ""}, WallpaperPreviewCache.parseTags(null));
    }
}
