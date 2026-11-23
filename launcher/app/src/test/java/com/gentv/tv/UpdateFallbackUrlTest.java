package com.gentv.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class UpdateFallbackUrlTest {
    @Test public void acceptsOnlyExpectedGithubReleaseShape() {
        assertTrue(TvCloud.allowedUpdateUrl("https://github.com/Elaine-Lynn/GenTV/releases/download/"
                + "v3.7.13/gentv-tv-3.7.13.apk"));
        assertFalse(TvCloud.allowedUpdateUrl("http://github.com/Elaine-Lynn/GenTV/releases/download/"
                + "v3.7.13/gentv-tv-3.7.13.apk"));
        assertFalse(TvCloud.allowedUpdateUrl("https://github.com/other/GenTV/releases/download/"
                + "v3.7.13/gentv-tv-3.7.13.apk"));
        assertFalse(TvCloud.allowedUpdateUrl("https://github.com/Elaine-Lynn/GenTV/releases/download/"
                + "v3.7.13/../../gentv-tv-3.7.13.apk"));
    }
    @Test public void acceptsOnlyExpectedGameSplitAssets() {
        assertTrue(TvCloud.allowedGameModuleUrl("https://github.com/Elaine-Lynn/GenTV/releases/download/"
                + "game-modules-v3.7.14/game-armeabi_v7a.apk"));
        assertFalse(TvCloud.allowedGameModuleUrl("https://github.com/Elaine-Lynn/GenTV/releases/download/"
                + "game-modules-v3.7.14/../game-master.apk"));
        assertFalse(TvCloud.allowedGameModuleUrl("https://github.com/other/GenTV/releases/download/"
                + "game-modules-v3.7.14/game-master.apk"));
    }
}
