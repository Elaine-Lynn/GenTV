package com.gentv.tv;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MoreAppsTest {
    @Test public void entryAppearsOnlyAfterThirtyTiles() {
        assertEquals(30,MoreApps.HOME_LIMIT);
        assertFalse(MoreApps.hasOverflow(0));assertFalse(MoreApps.hasOverflow(29));
        assertFalse(MoreApps.hasOverflow(30));assertTrue(MoreApps.hasOverflow(31));
    }
    @Test public void catalogExcludesDesktopPackagesAndDeduplicatesRemainingPackages() {
        List<String> all=Arrays.asList("home.a","extra.b","home.c","extra.b","extra.d");
        Set<String> home=new HashSet<>(Arrays.asList("home.a","home.c"));
        assertEquals(Arrays.asList("extra.b","extra.d"),MoreApps.missing(all,s->s,home::contains));
    }
    @Test public void emptyCatalogAndFullyDisplayedCatalogHaveNoDuplicates() {
        assertTrue(MoreApps.missing(Collections.<String>emptyList(),s->s,s->false).isEmpty());
        assertTrue(MoreApps.missing(Arrays.asList("a","b"),s->s,s->true).isEmpty());
    }
}
