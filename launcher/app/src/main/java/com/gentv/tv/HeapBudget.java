package com.gentv.tv;

/** Some older TV firmware reports a normal device despite a very small app heap. */
final class HeapBudget {
    private HeapBudget() { }

    static boolean constrained(int memoryClassMb, boolean lowRamDevice) {
        return lowRamDevice || memoryClassMb <= 128;
    }
}
