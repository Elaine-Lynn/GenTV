package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class GamePluginVersionTest {
    @Test public void pluginUpdatesWithoutChangingHostVersion() {
        assertTrue(GamePluginVersion.compatible(2, 2, 37, 40, 37));
        assertTrue(GamePluginVersion.newer(1, 2));
        assertFalse(GamePluginVersion.newer(2, 2));
        assertFalse(GamePluginVersion.newer(2, 1));
    }
    @Test public void rejectsIncompatibleHostAndApi() {
        assertFalse(GamePluginVersion.compatible(2, 1, 37, 40, 37));
        assertFalse(GamePluginVersion.compatible(2, 2, 38, 40, 37));
        assertFalse(GamePluginVersion.compatible(2, 2, 30, 36, 37));
        assertFalse(GamePluginVersion.compatible(0, 2, 37, 40, 37));
        assertFalse(GamePluginVersion.compatible(2, 2, 40, 37, 37));
    }
}
