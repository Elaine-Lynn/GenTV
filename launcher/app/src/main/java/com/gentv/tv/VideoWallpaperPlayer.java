package com.gentv.tv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;

/** Plays a selected built-in loop or the phone-supplied MP4 behind Home's controls. */
final class VideoWallpaperPlayer implements TextureView.SurfaceTextureListener {
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    private final Context context;
    private final File file;
    private final TextureView video;
    private final ImageView heldFrame;
    private Bitmap heldBitmap;
    private Surface surface;
    private MediaPlayer player;
    private boolean active;
    private boolean paused;
    private boolean prepared;
    private boolean closed;
    private int videoWidth;
    private int videoHeight;
    private int bundledResource;

    VideoWallpaperPlayer(Context context, FrameLayout root) {
        this.context = context;
        File dir = Config.dir(context);
        file = UserWallpaper.isVideo(BundledWallpapers.selected(context))
                ? UserWallpaper.video(context) : dir == null ? null : new File(dir, "wallpaper.mp4");
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
            if (player != null && prepared && !player.isPlaying()) player.start();
            return;
        }
        bundledResource = BundledWallpapers.videoResource(context);
        if (bundledResource == 0 && (!BundledWallpapers.selected(context).isEmpty()
                && !UserWallpaper.isVideo(BundledWallpapers.selected(context))
                || file == null || !file.isFile() || file.length() < 12
                || file.length() > MAX_BYTES)) return;
        active = true;
        paused = false;
        video.setAlpha(0f);
        video.setVisibility(View.VISIBLE);
        if (surface != null && surface.isValid()) play();
    }

    void stop() {
        // Keep the last video frame behind Home while another page is open. Without it the
        // bundled still preview flashes until MediaPlayer renders its first frame on return.
        if (heldBitmap == null && active && video.isAvailable() && video.getAlpha() > 0f) {
            try {
                Bitmap frame = video.getBitmap(960, 540);
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
                Bitmap frame = video.getBitmap(960, 540);
                if (frame != null) {
                    heldBitmap = frame;
                    heldFrame.setImageBitmap(frame);
                    heldFrame.setVisibility(View.VISIBLE);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) { }
        }
        if (player != null && prepared && player.isPlaying()) player.pause();
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

    void close() {
        closed = true;
        stop();
        releaseHeldFrame();
        video.setSurfaceTextureListener(null);
        if (surface != null) { surface.release(); surface = null; }
    }

    private void play() {
        if (!active || closed || player != null || surface == null || !surface.isValid()) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            next.setSurface(surface);
            if (bundledResource != 0) {
                try (AssetFileDescriptor source = context.getResources().openRawResourceFd(bundledResource)) {
                    next.setDataSource(source.getFileDescriptor(), source.getStartOffset(), source.getLength());
                }
            } else next.setDataSource(file.getAbsolutePath());
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (player == mp) { videoWidth = width; videoHeight = height; crop(); }
            });
            next.setOnPreparedListener(mp -> {
                if (player != mp) return;
                prepared = true;
                if (active && !paused) mp.start();
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (player == mp && active && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    video.setAlpha(1f);
                    releaseHeldFrame();
                }
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> { failed(mp); return true; });
            next.prepareAsync();
        } catch (Exception error) {
            Log.w("GenTV.VideoWallpaper", "Cannot open video wallpaper", error);
            failed(next);
        }
    }

    private void failed(MediaPlayer failed) {
        if (player != failed) return;
        Log.w("GenTV.VideoWallpaper", "Video wallpaper unavailable; showing still wallpaper");
        release();
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
        if (player != null) { player.release(); player = null; }
        prepared = false;
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
