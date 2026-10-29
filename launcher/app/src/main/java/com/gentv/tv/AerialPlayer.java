package com.gentv.tv;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Silent fullscreen aerial playback, allocated only by the screensaver. */
final class AerialPlayer implements TextureView.SurfaceTextureListener {
    private final TextureView video;
    private Surface surface;
    private static String lastPlayedUrl;
    private final View fallback;
    private final FrameLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    interface Listener {
        void onFrame(AerialCaptions captions, int positionMs);
        void onPosition(int positionMs);
    }
    private static final class Film {
        final String url;
        final AerialCaptions captions;
        Film(String url, AerialCaptions captions) { this.url = url; this.captions = captions; }
    }
    private final List<Film> videos = new ArrayList<>();
    private final List<Film> round = new ArrayList<>();
    private final AerialCache cache;
    private final ExecutorService downloads = Executors.newSingleThreadExecutor();
    private AtomicBoolean cancelled = new AtomicBoolean(true);
    private final java.util.Set<Film> badFilms = new java.util.HashSet<>();
    private Film currentFilm, lastFilm;
    private int downloadFailures;
    private boolean downloading;
    private final Runnable downloadRetry = this::warmCache;
    private Listener listener;
    private final Runnable progress = this::reportProgress;
    void setListener(Listener listener) { this.listener = listener; }
    private MediaPlayer player;
    private boolean active, closed, prepared;
    private int failures, videoWidth, videoHeight;
    private final Runnable retry = this::play;
    private final Runnable timeout = () -> failed(player);

    AerialPlayer(Context context, FrameLayout root, View fallback) {
        cache = new AerialCache(new File(context.getFilesDir(), "aerial-cache"));
        this.root = root;
        this.fallback = fallback;
        video = new TextureView(context);
        video.setFocusable(false);
        video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        video.setSurfaceTextureListener(this);
        root.addView(video, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        try (InputStream in = context.getAssets().open("aerials.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            JSONArray entries = new JSONObject(out.toString("UTF-8")).getJSONArray("videos");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                String url = entry.getString("url");
                android.net.Uri uri = android.net.Uri.parse(url);
                if ("https".equals(uri.getScheme()) && "sylvan.apple.com".equals(uri.getHost())
                        && uri.getUserInfo() == null) {
                    List<AerialCaptions.Cue> cues = new ArrayList<>();
                    JSONArray captions = entry.optJSONArray("captions");
                    if (captions != null) for (int j = 0; j < captions.length(); j++) {
                        JSONArray cue = captions.optJSONArray(j);
                        if (cue != null && cue.optInt(0, -1) >= 0)
                            cues.add(new AerialCaptions.Cue(cue.getInt(0), cue.optString(1, ""), cue.optString(2, "")));
                    }
                    videos.add(new Film(url, new AerialCaptions(entry.optString("label", ""), cues)));
                }
            }
            Collections.shuffle(videos);
        } catch (Exception error) {
            android.util.Log.w("GenTV.Aerial", "Catalogue unavailable; using screensaver image", error);
        }
    }

    void start() {
        if (closed || active) return;
        active = true;
        cancelled = new AtomicBoolean(false);
        downloadFailures = 0;
        // Fill unused budget on later entries too; existing files are never evicted.
        warmCache();
        if (surface != null && surface.isValid()) play();
    }


    private void warmCache() {
        if (!active || closed || downloading || cancelled.get()) return;
        downloading = true;
        AtomicBoolean token = cancelled;
        downloads.execute(() -> {
            try {
                // Resume unfinished files before starting another video.
                List<Film> pending = new ArrayList<>(videos);
                pending.sort((a, b) -> Boolean.compare(cache.partial(b.url), cache.partial(a.url)));
                for (Film film : pending) {
                    if (token.get() || cache.count() >= AerialCache.MAX_VIDEOS) break;
                    if (cache.cached(film.url) != null) continue;
                    File result = cache.download(film.url, token::get);
                    if (result == null) {
                        // A later, smaller film may still fit without evicting anything.
                        continue; // The first candidate may itself exceed the entire budget.
                    }
                    handler.post(() -> {
                        if (!token.get() && active && player == null) play();
                    });
                }
            } catch (java.io.IOException error) {
                if (!token.get()) handler.post(() -> {
                    if (!token.get() && active && ++downloadFailures < 3)
                        handler.postDelayed(downloadRetry, 60_000);
                });
            } finally {
                handler.post(() -> {
                    downloading = false;
                    if (active && !closed && token != cancelled) warmCache();
                });
            }
        });
    }

    private Film pickCached() {
        round.removeIf(film -> cache.cached(film.url) == null || badFilms.contains(film));
        if (round.isEmpty()) {
            for (Film film : videos) if (cache.cached(film.url) != null && !badFilms.contains(film)) round.add(film);
            Collections.shuffle(round);
            if (round.size() > 1 && round.get(0).url.equals(lastPlayedUrl))
                Collections.swap(round, 0, 1);
        }
        if (round.isEmpty()) return null;
        lastFilm = round.remove(0);
        lastPlayedUrl = lastFilm.url;
        return lastFilm;
    }

    private void play() {
        if (!active || closed || (surface == null || !surface.isValid()) || videos.isEmpty()) return;
        release();
        showFallback();
        currentFilm = pickCached();
        if (currentFilm == null) return;
        File local = cache.cached(currentFilm.url);
        if (local == null) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            next.setSurface(surface);
            next.setVolume(0f, 0f);
            next.setOnVideoSizeChangedListener((mp, w, h) -> {
                if (player != mp) return;
                videoWidth = w; videoHeight = h; crop();
            });
            next.setOnPreparedListener(mp -> {
                if (player == mp && active) { prepared = true; mp.start(); }
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (player == mp && active && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    handler.removeCallbacks(timeout);
                    failures = 0;
                    if (listener != null) listener.onFrame(currentFilm.captions, mp.getCurrentPosition());
                    reportProgress();
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                        fallback.animate().alpha(0f).setDuration(900)
                                .withEndAction(() -> fallback.setVisibility(View.GONE)).start();
                    } else fallback.setVisibility(View.GONE);
                    android.util.Log.i("GenTV.Aerial", "Screensaver video rendering");
                } else if (player == mp && what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    handler.removeCallbacks(timeout); handler.postDelayed(timeout, 30_000);
                } else if (player == mp && what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    handler.removeCallbacks(timeout);
                }
                return false;
            });
            next.setOnCompletionListener(mp -> { if (player == mp) play(); });
            next.setOnErrorListener((mp, what, extra) -> { failed(mp); return true; });
            next.setDataSource(local.getAbsolutePath());
            next.prepareAsync();
            handler.postDelayed(timeout, 30_000);
        } catch (Exception error) { failed(next); }
    }

    private void reportProgress() {
        handler.removeCallbacks(progress);
        if (!active || !prepared || player == null || listener == null) return;
        int position = player.getCurrentPosition();
        listener.onPosition(position);
        if (position < ScreensaverInfo.SHOW_MS) handler.postDelayed(progress, 250);
    }

    private void failed(MediaPlayer failed) {
        if (failed == null || player != failed) return;
        android.util.Log.w("GenTV.Aerial", "Video unavailable; using screensaver image");
        showFallback();
        release();
        // Bad local files are excluded this session; never repeatedly redownload an undecodable film.
        if (currentFilm != null) { badFilms.add(currentFilm); round.remove(currentFilm); }
        // Bounded local playback retries.
        if (active && !closed) handler.postDelayed(retry, ++failures < 3 ? 2000 : 60_000);
    }

    private void crop() {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0 || videoWidth == 0 || videoHeight == 0) return;
        float scale = Math.max((float) w / videoWidth, (float) h / videoHeight);
        video.setLayoutParams(new FrameLayout.LayoutParams(
                Math.round(videoWidth * scale), Math.round(videoHeight * scale), Gravity.CENTER));
    }

    private void showFallback() {
        fallback.animate().cancel();
        fallback.setAlpha(1f);
        fallback.setVisibility(View.VISIBLE);
    }

    private void release() {
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(retry);
        handler.removeCallbacks(progress);
        if (player != null) { player.release(); player = null; }
        prepared = false;
    }

    private void cancelDownload() {
        cancelled.set(true);
        cache.cancel();
        handler.removeCallbacks(downloadRetry);
    }

    void pause() {
        active = false;
        cancelDownload();
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(retry);
        handler.removeCallbacks(progress);
        // Keep the last frame through the exit fade; release the decoder on destruction.
        if (player != null && prepared) {
            try { player.pause(); } catch (IllegalStateException error) { release(); }
        } else release();
    }
    void stop() { active = false; cancelDownload(); release(); showFallback(); }
    void close() { closed = true; stop(); downloads.shutdownNow(); }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        if (active) play();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        if (surface != null) { surface.release(); surface = null; }
        if (active) showFallback();
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
