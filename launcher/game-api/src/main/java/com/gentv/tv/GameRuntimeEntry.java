package com.gentv.tv;

import android.app.Activity;
import android.view.KeyEvent;

/** Stable host contract for a game archive loaded without Android package installation. */
public interface GameRuntimeEntry {
    void create(Activity activity);
    void start();
    void stop();
    boolean dispatchKeyEvent(KeyEvent event);
    void destroy();
}
