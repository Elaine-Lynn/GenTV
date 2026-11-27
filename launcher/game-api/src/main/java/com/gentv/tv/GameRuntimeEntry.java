package com.gentv.tv;

import android.app.Activity;
import android.view.KeyEvent;

/** Stable host contract for a game archive loaded without Android package installation. */
public interface GameRuntimeEntry {
    int API_VERSION = 2;
    String API_BASE_EXTRA = "gentv.game.api_base";
    String CONTENT_BASE_EXTRA = "gentv.game.content_base";
    String BASE_VERSION_EXTRA = "gentv.game.base_version";
    void create(Activity activity);
    void start();
    void stop();
    boolean dispatchKeyEvent(KeyEvent event);
    void destroy();
}
