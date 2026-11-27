package com.gentv.tv;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateFailoverTest {
    @Test public void successNeverContactsBackup() throws Exception {
        assertEquals("primary", UpdateFailover.load(() -> "primary", () -> { fail("Unexpected backup"); return ""; }));
    }
    @Test public void networkFailureImmediatelyUsesBackupOnce() throws Exception {
        int[] calls = {0, 0};
        assertEquals("backup", UpdateFailover.load(() -> { calls[0]++; throw new IOException("DNS"); },
                () -> { calls[1]++; return "backup"; }));
        assertArrayEquals(new int[]{1, 1}, calls);
    }
    @Test public void validationFailureAlsoUsesBackup() throws Exception {
        assertEquals("valid", UpdateFailover.load(() -> { throw new SecurityException("Wrong signer"); }, () -> "valid"));
    }
    @Test public void bothFailuresStopAndPreserveBothCauses() {
        int[] calls = {0};
        try {
            UpdateFailover.load(() -> { throw new IOException("primary"); }, () -> { calls[0]++; throw new IOException("backup"); });
            fail("Expected failure");
        } catch (Exception error) {
            assertEquals("backup", error.getCause().getMessage());
            assertEquals("primary", ((UpdateFailover.Failure) error).primary.getMessage());
            assertEquals(1, calls[0]);
        }
    }
    @Test public void restrictsGithubDownloadsToThisRepository() {
        String prefix = "https://github.com/Elaine-Lynn/GenTV/releases/download/";
        assertTrue(TvCloud.allowedUpdateUrl(prefix + "v3.7.18/gentv-tv-3.7.18.apk"));
        assertTrue(TvCloud.allowedUpdateUrl(prefix + "v3.7.17/gentv-tv-v3.7.17-code39.apk"));
        assertFalse(TvCloud.allowedUpdateUrl(prefix + "v3.7.18/../gentv-tv-3.7.18.apk"));
        assertFalse(TvCloud.allowedUpdateUrl("https://github.com/other/GenTV/releases/download/v3.7.18/gentv-tv-3.7.18.apk"));
        assertFalse(TvCloud.allowedUpdateUrl(prefix.replace("https:", "http:") + "v3.7.18/gentv-tv-3.7.18.apk"));
    }
}
