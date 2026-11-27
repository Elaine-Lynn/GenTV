package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineWallpaperGalleryTest {
    private static final String BASE = "https://api.example.com/tv/";
    @Test public void acceptsVersionedImagesOnConfiguredServer() {
        assertTrue(OnlineWallpaperGallery.allowed(BASE + "wallpapers/sky.jpg?v=abc", BASE));
        assertTrue(OnlineWallpaperGallery.allowed(BASE + "wallpapers/preview.webp", BASE));
    }
    @Test public void rejectsForeignSourcesCredentialsAndTraversal() {
        for (String value : new String[]{"http://api.example.com/tv/wallpapers/a.jpg",
                "https://other.example/tv/wallpapers/a.jpg", BASE + "wallpapers/../update",
                BASE + "wallpapers/%2e%2e%2fupdate", BASE + "wallpapers/a.jpg#x",
                "https://user@api.example.com/tv/wallpapers/a.jpg", BASE + "wallpapers/",
                "file:///data/private.jpg"}) assertFalse(value, OnlineWallpaperGallery.allowed(value, BASE));
        assertFalse(OnlineWallpaperGallery.allowed(BASE + "wallpapers/a.jpg", ""));
    }
}
