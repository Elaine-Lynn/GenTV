package com.gentv.tv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Refreshes current weather only while Home is visible; keeps a short offline cache. */
final class WeatherWidget {
    private static final long REFRESH_MS = 30 * 60_000L;
    private static final long FAILED_RETRY_MS = 2 * 60_000L;
    private static final int IMMEDIATE_RETRIES = 2;
    private static final long CACHE_MS = 3 * 60 * 60_000L;
    private final Activity activity;
    private final TextView label;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::refresh;
    private boolean active, closed, fetching;

    WeatherWidget(Activity activity, TextView label) {
        this.activity = activity;
        this.label = label;
    }

    void start() {
        if (closed || active) return;
        active = true;
        refresh();
        render();
    }

    void stop() {
        active = false;
        main.removeCallbacks(tick);
    }

    void close() {
        closed = true;
        stop();
        worker.shutdownNow();
    }

    private void render() {
        String city = WeatherStore.city(activity);
        long age = System.currentTimeMillis() - WeatherStore.updated(activity);
        WeatherApi.Current current = WeatherStore.cached(activity);
        if (city.isEmpty()) {
            label.setText(fetching ? R.string.weather_locating : R.string.weather_pick_city);
            label.setVisibility(View.VISIBLE);
            return;
        }
        if (current == null || age < 0 || age > CACHE_MS) {
            label.setText(activity.getString(R.string.weather_status, city,
                    activity.getString(fetching ? R.string.weather_loading : R.string.weather_unavailable)));
        } else {
            label.setText(activity.getString(R.string.weather_home_detail,
                    WeatherApi.symbol(current.code), current.temperature, city,
                    activity.getString(WeatherApi.condition(current.code)),
                    current.maximum, current.minimum));
        }
        label.setVisibility(View.VISIBLE);
    }

    private void refresh() {
        main.removeCallbacks(tick);
        if (!active || closed || fetching) return;
        if (WeatherStore.city(activity).isEmpty()) { locate(); return; }
        long now = System.currentTimeMillis();
        long elapsed = now - WeatherStore.lastAttempt(activity);
        long interval = WeatherStore.updated(activity) < WeatherStore.lastAttempt(activity)
                ? FAILED_RETRY_MS : REFRESH_MS;
        if (elapsed >= 0 && elapsed < interval) {
            main.postDelayed(tick, interval - elapsed);
            return;
        }
        String city = WeatherStore.city(activity);
        double latitude = WeatherStore.latitude(activity), longitude = WeatherStore.longitude(activity);
        WeatherStore.attempted(activity, now);
        fetching = true;
        worker.execute(() -> {
            WeatherApi.Current result = null;
            for (int attempt = 0; attempt <= IMMEDIATE_RETRIES && result == null; attempt++) {
                try { result = WeatherApi.current(latitude, longitude); }
                catch (Exception error) {
                    android.util.Log.w("GenTV.Weather", "Current weather request failed (attempt "
                            + (attempt + 1) + "/" + (IMMEDIATE_RETRIES + 1) + ")", error);
                }
            }
            WeatherApi.Current weather = result;
            main.post(() -> {
                fetching = false;
                if (closed) return;
                if (weather != null && city.equals(WeatherStore.city(activity))
                        && latitude == WeatherStore.latitude(activity) && longitude == WeatherStore.longitude(activity))
                    WeatherStore.save(activity, weather, System.currentTimeMillis());
                if (active) { render(); refresh(); }
            });
        });
    }

    private void locate() {
        if (!TvCloud.enabled()) return;
        long now = System.currentTimeMillis();
        long retryDelay = WeatherStore.locationRetryDelay(activity, now);
        if (retryDelay > 0) {
            main.postDelayed(tick, retryDelay);
            return;
        }
        WeatherStore.locationAttempted(activity, now);
        fetching = true;
        render();
        worker.execute(() -> {
            WeatherApi.City result = null;
            try { result = WeatherApi.locate(activity); }
            catch (Exception ignored) { android.util.Log.w("GenTV.Weather", "Automatic city lookup unavailable"); }
            WeatherApi.City located = result;
            main.post(() -> {
                fetching = false;
                if (closed) return;
                // The user may have selected a city while this request was in flight.
                if (located != null && WeatherStore.city(activity).isEmpty()) WeatherStore.select(activity, located);
                if (!active) return;
                render();
                if (WeatherStore.city(activity).isEmpty()) {
                    long delay = WeatherStore.locationRetryDelay(activity, System.currentTimeMillis());
                    if (delay > 0) main.postDelayed(tick, delay);
                }
                else refresh();
            });
        });
    }
}
