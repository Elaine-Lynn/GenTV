package com.gentv.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.File;

/** One muted video surface for the personalization preview. Focus changes never wait for decoding. */
final class BundledVideoPreview implements TextureView.SurfaceTextureListener {
    private final Context context;
    private final TextureView video;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable delayedStart = this::startIfReady;
    private final Runnable delayedStop = this::stopIfStill;
    private Surface surface;
    private MediaPlayer player;
    private int requestedResource;
    private int playingResource;
    private File requestedFile;
    private File playingFile;
    private int videoWidth;
    private int videoHeight;
    private boolean ready;
    private boolean closed;

    BundledVideoPreview(Context context, TextureView video) {
        this.context = context;
        this.video = video;
        video.setSurfaceTextureListener(this);
        video.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> crop());
    }

    void select(int resource) {
        if (closed || requestedResource == resource && requestedFile == null && player != null) return;
        main.removeCallbacks(delayedStart);
        main.removeCallbacks(delayedStop);
        ready = false;
        requestedResource = resource;
        requestedFile = null;
        video.animate().cancel();
        video.setAlpha(0f);
        if (resource == 0) {
            // Keep the decoder off the focus path; rapid row changes cancel this cleanup.
            main.postDelayed(delayedStop, 160);
            return;
        }
        video.setVisibility(View.VISIBLE);
        if (player != null && playingResource == resource) {
            video.animate().alpha(1f).setDuration(120).start();
            return;
        }
        // Do not open a decoder while focus is merely passing over a thumbnail.
        main.postDelayed(delayedStart, 160);
    }

    void selectFile(File file) {
        if (closed || file == null || !file.isFile()) { select(0); return; }
        if (file.equals(requestedFile) && player != null) return;
        main.removeCallbacks(delayedStart);
        main.removeCallbacks(delayedStop);
        ready = false;
        requestedResource = 0;
        requestedFile = file;
        video.animate().cancel();
        video.setAlpha(0f);
        video.setVisibility(View.VISIBLE);
        main.postDelayed(delayedStart, 160);
    }

    void stop() {
        main.removeCallbacks(delayedStart);
        main.removeCallbacks(delayedStop);
        requestedResource = 0;
        requestedFile = null;
        ready = false;
        release();
        video.animate().cancel();
        video.setAlpha(0f);
        video.setVisibility(View.GONE);
    }

    void close() {
        stop();
        closed = true;
        video.setSurfaceTextureListener(null);
        if (surface != null) { surface.release(); surface = null; }
    }

    private void stopIfStill() {
        if (requestedResource == 0 && requestedFile == null) { release(); video.setVisibility(View.GONE); }
    }

    private void startIfReady() {
        ready = true;
        if (surface == null || !surface.isValid()
                || requestedResource == 0 && requestedFile == null) return;
        if (player != null) release();
        MediaPlayer next = new MediaPlayer();
        player = next;
        playingResource = requestedResource;
        playingFile = requestedFile;
        try {
            next.setSurface(surface);
            if (requestedFile != null) next.setDataSource(requestedFile.getAbsolutePath());
            else try (AssetFileDescriptor source = context.getResources().openRawResourceFd(requestedResource)) {
                next.setDataSource(source.getFileDescriptor(), source.getStartOffset(), source.getLength());
            }
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (player == mp) { videoWidth = width; videoHeight = height; crop(); }
            });
            next.setOnPreparedListener(mp -> { if (player == mp) mp.start(); });
            next.setOnInfoListener((mp, what, extra) -> {
                if (player == mp && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)
                    video.animate().alpha(1f).setDuration(240).start();
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> { if (player == mp) stop(); return true; });
            next.prepareAsync();
        } catch (Exception error) {
            stop();
        }
    }

    private void crop() {
        int width = video.getWidth(), height = video.getHeight();
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return;
        float scale = Math.max((float) width / videoWidth, (float) height / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(videoWidth * scale / width, videoHeight * scale / height,
                width / 2f, height / 2f);
        video.setTransform(matrix);
    }

    private void release() {
        if (player != null) { player.release(); player = null; }
        playingResource = 0;
        playingFile = null;
        videoWidth = 0;
        videoHeight = 0;
    }

    private void startIfReadySurface() { if (ready) startIfReady(); }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        startIfReadySurface();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { crop(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
