package com.gentv.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Map;
import org.junit.Test;

public class AppIconMirrorTest {
    private static final String A = "8695f52ca632f51a10af1237e8bad1c564e6313bc5d01958808ca07a269e68de";

    @Test public void parsesTheServerListAndKeepsOnlyFileNames() throws Exception {
        Map<String, String> icons = AppIconMirror.parse("{\"icons\":["
                + "{\"package\":\"com.dangbeimarket\",\"url\":\"https://api.gentv.app/tv/app-icons/" + A + ".png\",\"width\":640,\"height\":360},"
                + "{\"package\":\"com.dangbeimarket\",\"url\":\"https://api.gentv.app/tv/app-icons/" + A.replace('8', '9') + ".png\"},"
                + "{\"package\":\"cn.beevideo\",\"url\":\"https://elsewhere.example/" + A + ".jpg\"}]}");
        assertEquals(2, icons.size());
        assertEquals(A + ".png", icons.get("com.dangbeimarket"));   // first entry for a package wins
        // Only the file name is kept: images are always fetched from the GenTV API, never the listed host.
        assertEquals(A + ".jpg", icons.get("cn.beevideo"));
    }

    @Test public void skipsInvalidPackagesAndNames() throws Exception {
        Map<String, String> icons = AppIconMirror.parse("{\"icons\":["
                + "{\"package\":\"../etc\",\"url\":\"x/" + A + ".png\"},"
                + "{\"package\":\"nodots\",\"url\":\"x/" + A + ".png\"},"
                + "{\"package\":\"com.ok.tv\",\"url\":\"x/../index.json\"},"
                + "{\"package\":\"com.ok.tv\",\"url\":\"x/" + A + ".gif\"},"
                + "\"junk\"]}");
        assertTrue(icons.isEmpty());
        assertTrue(AppIconMirror.parse("{}").isEmpty());
    }

    @Test public void namesAreContentHashes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AppIconMirror.sha256("abc".getBytes(Charset.forName("UTF-8"))));
        assertTrue(AppIconMirror.FILE_NAME.matcher(A + ".webp").matches());
    }
}
