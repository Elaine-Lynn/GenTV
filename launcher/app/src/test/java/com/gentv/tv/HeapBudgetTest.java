package com.gentv.tv;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HeapBudgetTest {
    @Test public void smallHeapUsesConstrainedModeEvenWithoutLowRamFlag() {
        assertTrue(HeapBudget.constrained(96, false));
        assertTrue(HeapBudget.constrained(128, false));
        assertTrue(HeapBudget.constrained(256, true));
        assertFalse(HeapBudget.constrained(256, false));
    }
}
