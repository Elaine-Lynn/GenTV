package com.gentv.game;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** GeckoView game host; kept separate so the API 17 TV launcher stays installable. */
public final class KartGameActivity extends Activity {
    private static final String GAME_URL = "https://kart.igame86.com/play/";
    private static final String INPUT_EXTENSION = "resource://android/assets/game_input/";
    private static final String INPUT_EXTENSION_ID = "game-input@gentv.local";
    private static GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView browser;
    private FrameLayout root;
    private LanGameController controller;
    private LinearLayout connectionCard;
    private TextView loadMessage;
    private TextView controllerMessage;
    private volatile WebExtension.Port inputPort;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> held = new HashSet<>();
    private final Map<String, Long> lastPress = new HashMap<>();
    private final Map<String, Long> keyDownAt = new HashMap<>();
    private final Runnable releaseStale = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            for (String key : new HashSet<>(held)) {
                Long last = lastPress.get(key);
                if (last != null && now - last > 1800) input(key, false);
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xff0d1326);
        browser = new GeckoView(this);
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});
        session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
        session.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession session, String url) {
                if (loadMessage != null) loadMessage.setVisibility(View.GONE);
            }
            @Override public void onPageStop(GeckoSession session, boolean success) {
                if (!success) showLoadError(root);
                else browser.requestFocus();
            }
        });
        if (runtime == null) runtime = GeckoRuntime.create(this);
        session.open(runtime);
        browser.setSession(session);
        installInputBridge();
        session.loadUri(GAME_URL);
        handler.postDelayed(releaseStale, 500);
    }

    @Override protected void onStart() {
        super.onStart();
        session.setActive(true);
        startController();
    }

    private void startController() {
        if (connectionCard != null) {
            root.removeView(connectionCard);
            connectionCard = null;
        }
        if (controllerMessage != null) controllerMessage.setVisibility(View.GONE);
        String address = LanGameController.localAddress();
        if (address != null) {
            String code = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10_000));
            try {
                controller = new LanGameController(this, address, code,
                        new LanGameController.Listener() {
                            @Override public boolean ready() { return inputPort != null; }
                            @Override public void key(String key, boolean down) {
                                runOnUiThread(() -> input(key, down));
                            }
                        });
                showConnection(root, controller.url(), code);
            } catch (IOException ignored) { showMessage(root, "局域网遥控器启动失败"); }
        } else showMessage(root, "连接局域网后可使用手机遥控器");
    }

    @Override protected void onStop() {
        if (controller != null) { controller.close(); controller = null; }
        for (String key : new HashSet<>(held)) input(key, false);
        session.setActive(false);
        super.onStop();
    }

    private void installInputBridge() {
        runtime.getWebExtensionController().ensureBuiltIn(INPUT_EXTENSION, INPUT_EXTENSION_ID).accept(
                extension -> runOnUiThread(() -> {
                    if (session == null) return;
                    session.getWebExtensionController().setMessageDelegate(extension,
                        new WebExtension.MessageDelegate() {
                            @Override public void onConnect(WebExtension.Port port) {
                                inputPort = port;
                                port.setDelegate(new WebExtension.PortDelegate() {
                                    @Override public void onDisconnect(WebExtension.Port disconnected) {
                                        if (inputPort == disconnected) inputPort = null;
                                    }
                                });
                                Log.i("GenTVGame", "Web input bridge ready");
                            }
                        }, "gentv.game");
                }),
                error -> Log.e("GenTVGame", "Web input bridge unavailable", error));
    }

    private void showLoadError(FrameLayout root) {
        if (loadMessage == null) {
            loadMessage = label("游戏加载失败，按确定重试", 18, true);
            loadMessage.setGravity(Gravity.CENTER);
            loadMessage.setPadding(dp(24), dp(16), dp(24), dp(16));
            loadMessage.setBackgroundColor(0xee12273d);
            loadMessage.setFocusable(true);
            loadMessage.setOnClickListener(v -> session.loadUri(GAME_URL));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
            root.addView(loadMessage, params);
        }
        loadMessage.setVisibility(View.VISIBLE);
        loadMessage.requestFocus();
    }

    private void showConnection(FrameLayout root, String url, String code) {
        connectionCard = new LinearLayout(this);
        connectionCard.setOrientation(LinearLayout.VERTICAL);
        connectionCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xee12273d);
        background.setCornerRadius(dp(17));
        background.setStroke(dp(2), 0xff5ed8dd);
        connectionCard.setBackground(background);
        connectionCard.addView(label("扫码连接手机遥控器", 17, true));
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(qrCode(url + "#code=" + code));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(8);
        connectionCard.addView(qr, qrParams);
        TextView address = label(url, 11, false);
        address.setGravity(Gravity.CENTER);
        connectionCard.addView(address);
        TextView pin = label("电视码  " + code, 17, true);
        pin.setGravity(Gravity.CENTER);
        connectionCard.addView(pin);
        TextView hint = label("菜单键显示或隐藏", 11, false);
        hint.setGravity(Gravity.CENTER);
        connectionCard.addView(hint);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(180), -2,
                Gravity.TOP | Gravity.END);
        params.topMargin = dp(18);
        params.rightMargin = dp(18);
        root.addView(connectionCard, params);
    }

    private void showMessage(FrameLayout root, String message) {
        if (controllerMessage == null) {
            controllerMessage = label(message, 18, true);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            root.addView(controllerMessage, params);
        }
        controllerMessage.setText(message);
        controllerMessage.setVisibility(View.VISIBLE);
    }

    private void input(String key, boolean down) {
        if (browser == null) return;
        if ("Back".equals(key)) {
            if (down) finish();
            return;
        }
        if (down) {
            lastPress.put(key, SystemClock.elapsedRealtime());
            if (!held.add(key)) return;
        } else {
            lastPress.remove(key);
            if (!held.remove(key)) return;
        }
        if (connectionCard != null) connectionCard.setVisibility(View.GONE);
        if (inputPort != null) {
            try {
                JSONObject message = new JSONObject();
                message.put("key", key);
                message.put("down", down);
                inputPort.postMessage(message);
                return;
            } catch (JSONException | IllegalStateException error) {
                Log.w("GenTVGame", "Web input bridge failed", error);
            }
        }
        int code;
        switch (key) {
            case "ArrowUp": code = KeyEvent.KEYCODE_DPAD_UP; break;
            case "ArrowDown": code = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "ArrowLeft": code = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "ArrowRight": code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "Shift": code = KeyEvent.KEYCODE_SHIFT_LEFT; break;
            case "Space": code = KeyEvent.KEYCODE_SPACE; break;
            case "Enter": code = KeyEvent.KEYCODE_ENTER; break;
            case "Escape": code = KeyEvent.KEYCODE_ESCAPE; break;
            case "r": code = KeyEvent.KEYCODE_R; break;
            default: return;
        }
        long now = SystemClock.uptimeMillis();
        Long previous = keyDownAt.get(key);
        long started = down || previous == null ? now : previous;
        if (down) keyDownAt.put(key, started);
        else keyDownAt.remove(key);
        KeyEvent event = new KeyEvent(started, now,
                down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
        dispatchKeyEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && connectionCard != null) {
            connectionCard.setVisibility(connectionCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(releaseStale);
        if (controller != null) controller.close();
        for (String key : new HashSet<>(held)) input(key, false);
        inputPort = null;
        if (session != null) { session.close(); session = null; }
        browser = null;
        root = null;
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(Color.WHITE);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }
    private Bitmap qrCode(String url) {
        try {
            BitMatrix bits = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320);
            Bitmap image = Bitmap.createBitmap(320, 320, Bitmap.Config.RGB_565);
            for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++)
                image.setPixel(x, y, bits.get(x, y) ? Color.BLACK : Color.WHITE);
            return image;
        } catch (Exception ignored) { return null; }
    }
}
