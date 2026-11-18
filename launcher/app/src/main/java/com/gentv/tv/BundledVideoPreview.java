package com.gentv.tv;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.File;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.video.VideoSize;

/** One muted video surface for the personalization preview. Focus changes never wait for decoding. */
final class BundledVideoPreview implements TextureView.SurfaceTextureListener {
    private final Context context;
    private final TextureView video;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable delayedStart = this::startIfReady;
    private final Runnable delayedStop = this::stopIfStill;
    private Surface surface;
    private ExoPlayer player;
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
        ExoPlayer next = new ExoPlayer.Builder(context).build();
        player = next;
        playingResource = requestedResource;
        playingFile = requestedFile;
        try {
            next.setVideoSurface(surface);
            Uri uri = requestedFile != null ? Uri.fromFile(requestedFile)
                    : RawResourceDataSource.buildRawResourceUri(requestedResource);
            next.setMediaItem(MediaItem.fromUri(uri));
            next.setVolume(0f);
            next.setRepeatMode(Player.REPEAT_MODE_ONE);
            next.addListener(new Player.Listener() {
                @Override public void onVideoSizeChanged(VideoSize size) {
                    if (player == next) {
                        videoWidth = size.width;
                        videoHeight = size.height;
                        crop();
                    }
                }
                @Override public void onRenderedFirstFrame() {
                    if (player == next && requestedResource == playingResource
                            && requestedFile == playingFile)
                        video.animate().alpha(1f).setDuration(240).start();
                }
                @Override public void onPlayerError(PlaybackException error) {
                    Log.w("GenTV.VideoPreview", "Cannot play wallpaper preview", error);
                    if (player == next) stop();
                }
            });
            next.prepare();
            next.play();
        } catch (Exception error) {
            Log.w("GenTV.VideoPreview", "Cannot open wallpaper preview", error);
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
