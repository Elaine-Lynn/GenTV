package com.gentv.tv;

import android.content.Context;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.video.VideoSize;

/** Plays a selected built-in loop or the phone-supplied MP4 behind Home's controls. */
final class VideoWallpaperPlayer implements TextureView.SurfaceTextureListener {
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    // The temporary still is only a transition placeholder, not the actual video texture.
    private final Context context;
    private final boolean constrainedHeap;
    private final int holdWidth;
    private final int holdHeight;
    private final File file;
    private final TextureView video;
    private final ImageView heldFrame;
    private Bitmap heldBitmap;
    private Surface surface;
    private ExoPlayer player;
    private boolean active;
    private boolean paused;
    private boolean closed;
    private int videoWidth;
    private int videoHeight;
    private int bundledResource;
    private long lastLoopAt;
    private long resumePositionMs;
    private String playbackSource;

    VideoWallpaperPlayer(Context context, FrameLayout root) {
        this.context = context;
        constrainedHeap = CompatApi.constrainedHeap(CompatApi.service(context, ActivityManager.class));
        holdWidth = constrainedHeap ? 256 : 480;
        holdHeight = constrainedHeap ? 144 : 270;
        File dir = Config.dir(context);
        file = UserWallpaper.isVideo(BundledWallpapers.selected(context))
                ? UserWallpaper.video(context, BundledWallpapers.selected(context))
                : dir == null ? null : new File(dir, "wallpaper.mp4");
        video = new TextureView(context);
        heldFrame = new ImageView(context);
        heldFrame.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heldFrame.setVisibility(View.GONE);
        heldFrame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(heldFrame, root.indexOfChild(root.findViewById(R.id.racePanel)),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        video.setFocusable(false);
        video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        video.setSurfaceTextureListener(this);
        video.setVisibility(View.GONE);
        // Above both still-wallpaper layers, below the panel, clock and app tiles.
        root.addView(video, root.indexOfChild(root.findViewById(R.id.racePanel)),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        video.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> crop());
    }

    void start() {
        if (closed) return;
        if (active) {
            paused = false;
            if (player != null) player.play();
            return;
        }
        bundledResource = BundledWallpapers.videoResource(context);
        if (bundledResource == 0 && (!BundledWallpapers.selected(context).isEmpty()
                && !UserWallpaper.isVideo(BundledWallpapers.selected(context))
                || file == null || !file.isFile() || file.length() < 12
                || file.length() > MAX_BYTES)) return;
        String source = bundledResource != 0 ? "res:" + bundledResource
                : file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        if (!source.equals(playbackSource)) {
            playbackSource = source;
            resumePositionMs = 0;
        }
        active = true;
        paused = false;
        video.setAlpha(0f);
        video.setVisibility(View.VISIBLE);
        if (surface != null && surface.isValid()) play();
    }

    void stop() {
        // Keep the last video frame behind Home while another page is open. Without it the
        // bundled still preview flashes until ExoPlayer renders its first frame on return.
        if (heldBitmap == null && active && video.isAvailable() && video.getAlpha() > 0f) {
            try {
                Bitmap frame = video.getBitmap(holdWidth, holdHeight);
                if (frame != null) {
                    releaseHeldFrame();
                    heldBitmap = frame;
                    heldFrame.setImageBitmap(frame);
                    heldFrame.setVisibility(View.VISIBLE);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) { }
        }
        active = false;
        paused = false;
        release();
        video.setVisibility(View.GONE);
    }

    /** A translucent panel still shows Home behind it, so keep its last video frame visible. */
    void pause() {
        paused = true;
        // Keep a frame before a secondary Activity stops this TextureView. Some TVs
        // return a black bitmap from getBitmap() once the window has been paused.
        if (heldBitmap == null && active && video.isAvailable() && video.getAlpha() > 0f) {
            try {
                Bitmap frame = video.getBitmap(holdWidth, holdHeight);
                if (frame != null) {
                    heldBitmap = frame;
                    heldFrame.setImageBitmap(frame);
                    heldFrame.setVisibility(View.VISIBLE);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) { }
        }
        if (player != null) player.pause();
    }

    Bitmap frameForBlur() {
        if (!active) return null;
        try {
            if (video.getVisibility() == View.VISIBLE && video.getAlpha() >= 1f
                    && video.isAvailable()) return video.getBitmap(192, 108);
            if (heldBitmap != null && !heldBitmap.isRecycled())
                return Bitmap.createScaledBitmap(heldBitmap, 192, 108, true);
            return null;
        }
        catch (Exception | OutOfMemoryError ignored) { return null; }
    }

    boolean isActive() { return active; }
    boolean usesSmallHeap() { return constrainedHeap; }

    void close() {
        closed = true;
        stop();
        resumePositionMs = 0;
        releaseHeldFrame();
        video.setSurfaceTextureListener(null);
        if (surface != null) { surface.release(); surface = null; }
    }

    private void play() {
        if (!active || closed || player != null || surface == null || !surface.isValid()) return;
        ExoPlayer.Builder builder = new ExoPlayer.Builder(context);
        if (constrainedHeap) builder.setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 5000, 500, 1000)
                .setTargetBufferBytes(8 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build());
        ExoPlayer next = builder.build();
        player = next;
        try {
            next.setVideoSurface(surface);
            Uri uri = bundledResource != 0
                    ? RawResourceDataSource.buildRawResourceUri(bundledResource)
                    : Uri.fromFile(file);
            next.setMediaItem(MediaItem.fromUri(uri));
            if (resumePositionMs > 0) next.seekTo(resumePositionMs);
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
                    if (player != next || !active) return;
                    if (lastLoopAt == 0) lastLoopAt = SystemClock.elapsedRealtime();
                    video.setAlpha(1f);
                    releaseHeldFrame();
                }
                @Override public void onMediaItemTransition(MediaItem item, int reason) {
                    if (player != next || reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return;
                    long now = SystemClock.elapsedRealtime();
                    if (lastLoopAt != 0)
                        Log.i("GenTV.VideoWallpaper", "ExoPlayer loop elapsedMs=" + (now - lastLoopAt));
                    lastLoopAt = now;
                }
                @Override public void onPlayerError(PlaybackException error) {
                    Log.w("GenTV.VideoWallpaper", "ExoPlayer video wallpaper failed", error);
                    failed(next);
                }
            });
            next.prepare();
            if (active && !paused) next.play();
        } catch (Exception error) {
            Log.w("GenTV.VideoWallpaper", "Cannot open video wallpaper", error);
            failed(next);
        }
    }

    private void failed(ExoPlayer failed) {
        if (player != failed) return;
        Log.w("GenTV.VideoWallpaper", "Video wallpaper unavailable; showing still wallpaper");
        release();
        resumePositionMs = 0;
        active = false;
        video.setVisibility(View.GONE);
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
        if (player != null) {
            long position = player.getCurrentPosition();
            if (position > 0) resumePositionMs = position;
            player.release();
            player = null;
        }
        lastLoopAt = 0;
        videoWidth = 0;
        videoHeight = 0;
    }

    private void releaseHeldFrame() {
        heldFrame.setVisibility(View.GONE);
        heldFrame.setImageDrawable(null);
        if (heldBitmap != null) { heldBitmap.recycle(); heldBitmap = null; }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        if (active) play();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { crop(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
