package com.gentv.tv;

/** Public viewing intervals; retired short-test preferences migrate to the default. */
final class ChildWatchInterval {
    static int minutes(int stored, boolean retiredTestSelected) {
        if (retiredTestSelected) return 10;
        switch (stored) {
            case 5: case 10: case 15: case 20: return stored;
            default: return 10;
        }
    }
}
