package com.gentv.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/** Debug-only probe for loading an uninstalled game split and GeckoView from private storage. */
public final class GameRuntimeProbeActivity extends Activity {
    private static final String TAG = "GenTVRuntimeProbe";
    private TextView status;
    private GameRuntimeEntry game;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(22);
        status.setGravity(Gravity.CENTER);
        status.setText("Preparing runtime probe");
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        new Thread(() -> prepare(root), "game-runtime-probe").start();
    }

    private void prepare(FrameLayout root) {
        try {
            DexClassLoader loader = GameRuntimeStore.loader(this);
            Class<?> gameClass = loader.loadClass("com.gentv.game.KartGameScreen");
            Log.i(TAG, "DEX loaded: " + gameClass.getName());
            runOnUiThread(() -> startGame(gameClass));
        } catch (Throwable error) {
            fail("Preparation failed", error);
        }
    }

    private void startGame(Class<?> gameClass) {
        try {
            game = (GameRuntimeEntry) gameClass.newInstance();
            game.create(this);
            game.start();
            Log.i(TAG, "Game opened without PackageInstaller");
        } catch (Throwable error) {
            fail("GeckoView failed", error);
        }
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return game != null && game.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override protected void onStop() {
        if (game != null) game.stop();
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();
        if (game != null) game.start();
    }

    private void fail(String stage, Throwable error) {
        Throwable cause = error instanceof InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
        Log.e(TAG, stage, cause);
        runOnUiThread(() -> status.setText(stage + ": " + cause));
    }

    @Override protected void onDestroy() {
        if (game != null) game.destroy();
        super.onDestroy();
    }
}
