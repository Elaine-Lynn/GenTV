package com.gentv.weather;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GHCP 1 home content provider for Gen Weather.
 * Returns a seamless, borderless-ready {@code summary} snapshot (3 items, no replacement wallpaper)
 * immediately from local cache, and refreshes live Open-Meteo data asynchronously in the background.
 */
public final class WeatherContentService extends Service {
    private static final long STALE_MS = 15 * 60 * 1000L;

    private HandlerThread workerThread;
    private Handler workerHandler;
    private Messenger endpoint;
    private ExecutorService networkPool;
    private Messenger activeCallback;
    private int activeToken;
    private volatile boolean refreshing;

    private final SharedPreferences.OnSharedPreferenceChangeListener storeListener = (prefs, key) -> {
        if (WeatherStore.KEY_REVISION.equals(key) && workerHandler != null) {
            workerHandler.post(this::sendInvalidate);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        networkPool = Executors.newSingleThreadExecutor();
        workerThread = new HandlerThread("gen-weather-ghcp");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper(), this::handleMessage);
        endpoint = new Messenger(workerHandler);
        WeatherStore.prefs(this).registerOnSharedPreferenceChangeListener(storeListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return endpoint.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (workerHandler != null) {
            workerHandler.post(() -> activeCallback = null);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        WeatherStore.prefs(this).unregisterOnSharedPreferenceChangeListener(storeListener);
        activeCallback = null;
        if (networkPool != null) networkPool.shutdownNow();
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    private boolean handleMessage(Message request) {
        if (request.what != 1 || request.replyTo == null) return true;
        // Verify calling host using Android-assigned sendingUid, never caller-provided strings.
        String[] callers = getPackageManager().getPackagesForUid(request.sendingUid);
        if (callers == null || !Arrays.asList(callers).contains("com.gentv.tv")) return true;

        try {
            Bundle reqData = request.getData();
            if (reqData == null || reqData.getInt("protocolMajor", 0) != 1) return true;
            activeCallback = request.replyTo;
            activeToken = request.arg1;

            String localeTag = reqData.getString("locale", "");
            Locale hostLocale = localeTag.isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(localeTag);
            boolean zh = WeatherModels.isChinese(hostLocale);

            WeatherModels.City city = WeatherStore.activeCity(this);
            WeatherModels.WeatherData cached = WeatherStore.cachedWeather(this, city.id);
            boolean fahrenheit = WeatherStore.useFahrenheit(this);

            if (cached == null || System.currentTimeMillis() - cached.fetchedAtMs > STALE_MS) {
                triggerBackgroundRefresh(city);
            }

            JSONObject snapshot = buildSnapshot(city, cached, fahrenheit, zh);
            Message response = Message.obtain(null, 2);
            response.arg1 = request.arg1;
            Bundle out = new Bundle();
            out.putString("snapshot", snapshot.toString());
            response.setData(out);
            request.replyTo.send(response);
        } catch (Exception ignored) {
            // Host falls back safely on timeout or error.
        }
        return true;
    }

    private JSONObject buildSnapshot(WeatherModels.City city, WeatherModels.WeatherData data,
                                     boolean fahrenheit, boolean zh) throws Exception {
        String targetActivity = getPackageName() + "/.WeatherActivity";
        JSONArray items = new JSONArray();
        String subtitle;
        String snapshotId;

        if (data == null) {
            subtitle = zh ? "正在同步全球实时气象" : "Syncing live global weather";
            snapshotId = "weather-pending-" + city.id;
            items.put(new JSONObject()
                    .put("id", "temperature")
                    .put("title", "—")
                    .put("subtitle", zh ? "点击查看天气详情" : "Open for full forecast")
                    .put("activity", targetActivity)
                    .put("actionId", "weather"));
        } else {
            String cond = WeatherModels.conditionLabel(data.weatherCode, data.isDay, zh);
            String temp = WeatherModels.formatTemp(data.tempC, fahrenheit);
            String feels = WeatherModels.formatTemp(data.feelsLikeC, fahrenheit);
            String high = WeatherModels.formatTemp(data.highC, fahrenheit);
            String low = WeatherModels.formatTemp(data.lowC, fahrenheit);
            String windDir = WeatherModels.windDirection(data.windDeg, zh);
            String windSpeed = Math.round(data.windKmh) + " km/h";

            subtitle = zh
                    ? cond + " · 体感 " + feels + " · " + WeatherModels.poeticInsight(data, true)
                    : cond + " · Feels like " + feels;
            if (subtitle.length() > 120) subtitle = subtitle.substring(0, 120);
            snapshotId = "weather-" + city.id + "-" + (data.fetchedAtMs / 60000L);

            // Item 1: Hero Temperature + Action
            items.put(new JSONObject()
                    .put("id", "temperature")
                    .put("title", temp)
                    .put("subtitle", zh
                            ? cond + " · 最高 " + high + "  最低 " + low
                            : cond + " · H " + high + "  L " + low)
                    .put("activity", targetActivity)
                    .put("actionId", "weather"));

            // Item 2: Air Quality
            String aqiTitle = data.aqi >= 0
                    ? WeatherModels.aqiLabel(data.aqi, zh)
                    : (data.humidity + "%");
            String aqiSub = data.aqi >= 0
                    ? (zh ? "空气质量 · AQI " + data.aqi : "Air quality · AQI " + data.aqi)
                    : (zh ? "相对湿度" : "Humidity");
            items.put(new JSONObject()
                    .put("id", "air")
                    .put("title", aqiTitle)
                    .put("subtitle", aqiSub));

            // Item 3: Wind & Sunset
            String sunsetPart = data.sunset.isEmpty()
                    ? (zh ? "湿度 " + data.humidity + "%" : "Humidity " + data.humidity + "%")
                    : (zh ? "日落 " + data.sunset : "Sunset " + data.sunset);
            items.put(new JSONObject()
                    .put("id", "wind")
                    .put("title", windSpeed)
                    .put("subtitle", zh
                            ? windDir + " · " + sunsetPart
                            : "Wind " + windDir + " · " + sunsetPart));
        }

        return new JSONObject()
                .put("protocolMajor", 1)
                .put("snapshotId", snapshotId)
                .put("template", "summary")
                .put("privacy", "public")
                .put("ttlSeconds", 180)
                .put("title", city.name)
                .put("subtitle", subtitle)
                .put("items", items);
    }

    private void triggerBackgroundRefresh(WeatherModels.City city) {
        if (refreshing || networkPool == null || networkPool.isShutdown()) return;
        refreshing = true;
        networkPool.execute(() -> {
            try {
                WeatherModels.WeatherData fresh = WeatherApiClient.fetchWeather(city);
                WeatherStore.saveWeather(getApplicationContext(), fresh);
            } catch (Exception ignored) {
                // Keep cached snapshot.
            } finally {
                refreshing = false;
            }
        });
    }

    private void sendInvalidate() {
        if (activeCallback == null) return;
        try {
            Message invalidation = Message.obtain(null, 3);
            invalidation.arg1 = activeToken;
            activeCallback.send(invalidation);
        } catch (Exception ignored) {
            activeCallback = null;
        }
    }
}
