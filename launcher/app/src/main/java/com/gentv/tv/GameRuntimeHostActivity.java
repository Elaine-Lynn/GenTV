package com.gentv.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

/** Small Android host for the downloaded game's screen and lifecycle. */
public final class GameRuntimeHostActivity extends Activity {
    private GameRuntimeEntry game;
    private boolean visible;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView loading = new TextView(this);
        loading.setText("游戏加载中…");
        loading.setTextColor(Color.WHITE);
        loading.setBackgroundColor(0xff0d1326);
        loading.setGravity(Gravity.CENTER);
        loading.setTextSize(20);
        setContentView(loading);
        new Thread(() -> {
            try {
                Class<?> screen = GameRuntimeStore.loader(this)
                        .loadClass("com.gentv.game.KartGameScreen");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        game = (GameRuntimeEntry) screen.newInstance();
                        game.create(this);
                        if (visible) game.start();
                    } catch (Throwable error) { showError(loading, error); }
                });
            } catch (Throwable error) { runOnUiThread(() -> showError(loading, error)); }
        }, "game-runtime-loader").start();
    }

    private void showError(TextView loading, Throwable error) {
        Log.e("GenTVGameRuntime", "Game loading failed", error);
        loading.setText("游戏组件加载失败，请返回重试");
        setContentView(loading);
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        if (game != null) game.start();
    }

    @Override protected void onStop() {
        visible = false;
        if (game != null) game.stop();
        super.onStop();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return game != null && game.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        if (game != null) game.destroy();
        super.onDestroy();
    }
}
