package com.gentv.tv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class WallpaperThumbnailTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void fileNamesAreStableHexAndDistinct() {
        String name = WallpaperThumbnail.fileName("file:/data/a.jpg:1:2:1");
        assertEquals(name, WallpaperThumbnail.fileName("file:/data/a.jpg:1:2:1"));
        assertTrue(name.matches("[0-9a-f]{32}\\.jpg"));
        assertNotEquals(name, WallpaperThumbnail.fileName("file:/data/a.jpg:1:3:1"));
    }

    @Test public void fileKeyChangesWhenTheWallpaperIsReplaced() throws Exception {
        File wallpaper = temporary.newFile("user_wallpaper.image");
        Files.write(wallpaper.toPath(), new byte[]{1, 2, 3});
        assertTrue(wallpaper.setLastModified(1_000_000L));
        String before = WallpaperThumbnail.fileKey(wallpaper);
        assertEquals(before, WallpaperThumbnail.fileKey(wallpaper));
        Files.write(wallpaper.toPath(), new byte[]{1, 2, 3, 4});
        assertTrue(wallpaper.setLastModified(2_000_000L));
        assertNotEquals(before, WallpaperThumbnail.fileKey(wallpaper));
        assertTrue(before.endsWith(":" + WallpaperThumbnail.FORMAT));
    }

    @Test public void resourceKeyFollowsTheAppBuild() {
        assertEquals(WallpaperThumbnail.resourceKey(7, 100), WallpaperThumbnail.resourceKey(7, 100));
        assertNotEquals(WallpaperThumbnail.resourceKey(7, 100), WallpaperThumbnail.resourceKey(7, 101));
        assertNotEquals(WallpaperThumbnail.resourceKey(7, 100), WallpaperThumbnail.resourceKey(8, 100));
    }

    @Test public void sampleSizeKeepsAtLeastTheThumbnailSize() {
        assertEquals(1, WallpaperThumbnail.sampleSize(256, 144, 256, 144));
        assertEquals(1, WallpaperThumbnail.sampleSize(400, 225, 256, 144));
        assertEquals(4, WallpaperThumbnail.sampleSize(1280, 720, 256, 144));
        assertEquals(8, WallpaperThumbnail.sampleSize(3840, 2160, 256, 144));
        // A portrait picture is limited by its width.
        assertEquals(4, WallpaperThumbnail.sampleSize(1080, 1920, 256, 144));
        assertEquals(1, WallpaperThumbnail.sampleSize(100, 50, 256, 144));
    }

    @Test public void coverCropCentresTheFrame() {
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, WallpaperThumbnail.coverCrop(1920, 1080, 256, 144));
        // Wider than 16:9: crop the sides.
        assertArrayEquals(new int[]{240, 0, 1440, 810}, WallpaperThumbnail.coverCrop(1920, 810, 256, 144));
        // Portrait: crop top and bottom.
        assertArrayEquals(new int[]{0, 656, 1080, 608}, WallpaperThumbnail.coverCrop(1080, 1920, 256, 144));
        int[] tiny = WallpaperThumbnail.coverCrop(1, 1, 256, 144);
        assertTrue(tiny[2] >= 1 && tiny[3] >= 1);
    }

    @Test public void pickFindsTheShownRotationPicture() {
        File a = new File("wallpapers/a.jpg");
        File b = new File("wallpapers/b.jpg");
        assertSame(b, WallpaperThumbnail.pick(Arrays.asList(a, b), "b.jpg"));
        assertSame(a, WallpaperThumbnail.pick(Arrays.asList(a, b), "gone.jpg"));
        assertSame(a, WallpaperThumbnail.pick(Arrays.asList(a, b), ""));
        assertNull(WallpaperThumbnail.pick(Collections.<File>emptyList(), "a.jpg"));
        assertNull(WallpaperThumbnail.pick(null, "a.jpg"));
    }

    @Test public void blankDecoderFramesAreUniform() {
        int[] green = new int[144];
        // The empty frame a TV decoder returned for an imported video: YUV zero reads as dark green.
        for (int i = 0; i < green.length; i++) green[i] = 0xFF008601 + (i % 3 == 0 ? 0x000300 : 0);
        assertTrue(WallpaperThumbnail.uniform(green));
        int[] black = new int[144];
        Arrays.fill(black, 0xFF000000);
        assertTrue(WallpaperThumbnail.uniform(black));
        assertTrue(WallpaperThumbnail.uniform(new int[0]));
    }

    @Test public void darkOrSoftPicturesAreNotUniform() {
        int[] gradient = new int[144];
        // A night sky fading from near black to deep blue still has a visible spread.
        for (int i = 0; i < gradient.length; i++) gradient[i] = 0xFF000000 | (i / 12) << 16 | (i / 6) << 8 | (i / 3);
        assertFalse(WallpaperThumbnail.uniform(gradient));
        int[] spot = new int[144];
        Arrays.fill(spot, 0xFF202020);
        spot[70] = 0xFFE0E0E0;
        assertFalse(WallpaperThumbnail.uniform(spot));
    }
}
