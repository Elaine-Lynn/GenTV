package com.gentv.tv.pond;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Choreographer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Renders the pond behind Home. Input and all UI remain with the host. */
public final class PondSurface extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final long frameIntervalNs;
    private final PondRenderer pond;
    private final Runnable onFailure;
    private boolean running;
    private boolean ready;
    private long nextFrame;
    private final Choreographer.FrameCallback frame = this::scheduleFrame;

    public PondSurface(Context context, int count, int speedPercent, int fps, int renderHeight,
                       Runnable onFailure, java.util.function.Consumer<FishingSession.Catch> onCatch) {
        super(context);
        this.onFailure = onFailure;
        frameIntervalNs = 1_000_000_000L / fps;
        pond = new PondRenderer(context);
        pond.catchListener = fish -> post(() -> onCatch.accept(fish));
        int height = Math.min(renderHeight, getResources().getDisplayMetrics().heightPixels);
        getHolder().setFixedSize(height * 16 / 9, height);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        queueEvent(() -> {
            pond.world.setCount(count);
            pond.world.tempo = speedPercent / 100f;
        });
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setFocusable(false);
        setKeepScreenOn(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void scheduleFrame(long now) {
        if (!running) return;
        if (now + 750_000L >= nextFrame) {
            requestRender();
            nextFrame = Math.max(now, nextFrame) + frameIntervalNs;
        }
        Choreographer.getInstance().postFrameCallback(frame);
    }

    public void feed() {
        queueEvent(pond.world::feedRandom);
    }

    public void ripple() {
        queueEvent(() -> pond.world.disturb(900, 440));
    }

    public void fish() {
        queueEvent(pond.fishing::start);
    }

    @Override
    public void onResume() {
        if (running) return;
        queueEvent(pond::resetClock);
        super.onResume();
        running = true;
        nextFrame = 0;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    @Override
    public void onPause() {
        if (!running) return;
        stopFrames();
        super.onPause();
    }

    private void stopFrames() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFrames();
        super.onDetachedFromWindow();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        ready = false;
        try {
            pond.onSurfaceCreated(gl, config);
            ready = true;
        } catch (RuntimeException failure) {
            Log.w("GenTVPond", "Pond initialization failed", failure);
            post(onFailure);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (ready) pond.onSurfaceChanged(gl, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (ready) pond.onDrawFrame(gl);
    }
}
