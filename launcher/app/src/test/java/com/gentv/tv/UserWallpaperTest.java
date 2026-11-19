package com.gentv.tv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.*;

public class UserWallpaperTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void keepsLegacyWallpapersAndListsNewOnesNewestFirst() throws Exception {
        File old = new File(temporary.getRoot(), "user_wallpaper.image");
        write(old);
        File folder = temporary.newFolder("user_wallpapers");
        String photo = "user_image_0123456789abcdef0123456789abcdef";
        String video = "user_video_abcdef0123456789abcdef0123456789";
        File first = new File(folder, photo + ".image");
        File second = new File(folder, video + ".mp4");
        write(first);
        write(second);
        old.setLastModified(1000);
        first.setLastModified(2000);
        second.setLastModified(3000);
        List<String> ids = UserWallpaper.ids(temporary.getRoot());
        assertEquals(3, ids.size());
        assertEquals(video, ids.get(0));
        assertEquals(photo, ids.get(1));
        assertEquals(UserWallpaper.IMAGE_ID, ids.get(2));
    }

    @Test public void ignoresInvalidNamesAndPreviewFiles() throws Exception {
        File folder = temporary.newFolder("user_wallpapers");
        write(new File(folder, "user_video_abcdef0123456789abcdef0123456789.preview.jpg"));
        write(new File(folder, "user_image_not-a-uuid.image"));
        write(new File(folder, "user_video_abcdef0123456789abcdef0123456789.mp4"));
        assertEquals(1, UserWallpaper.ids(temporary.getRoot()).size());
        assertFalse(UserWallpaper.isImage("user_image_../bad"));
        assertTrue(UserWallpaper.isVideo("user_video_abcdef0123456789abcdef0123456789"));
    }

    @Test public void listsWebmAndMkvWallpapers() throws Exception {
        File folder = temporary.newFolder("user_wallpapers");
        String webm = "user_video_0123456789abcdef0123456789abcdef";
        String mkv = "user_video_abcdef0123456789abcdef0123456789";
        write(new File(folder, webm + ".webm"));
        write(new File(folder, mkv + ".mkv"));
        List<String> ids = UserWallpaper.ids(temporary.getRoot());
        assertEquals(2, ids.size());
        assertTrue(ids.contains(webm));
        assertTrue(ids.contains(mkv));
    }

    private static void write(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(1); }
    }
}
