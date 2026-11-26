package com.gentv.tv;

import java.lang.ref.WeakReference;

/** Main-thread visibility signal for the translucent toolbox; never retains an Activity. */
final class ToolboxBackdrop {
    private static boolean visible;
    private static WeakReference<Runnable> observer = new WeakReference<>(null);

    static boolean visible() { return visible; }
    static void observe(Runnable listener) { observer = new WeakReference<>(listener); listener.run(); }
    static void stopObserving(Runnable listener) { if (observer.get() == listener) observer.clear(); }
    static void visible(boolean value) {
        if (visible == value) return;
        visible = value;
        Runnable listener = observer.get();
        if (listener != null) listener.run();
    }
}
