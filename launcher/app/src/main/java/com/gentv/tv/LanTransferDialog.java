package com.gentv.tv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/**
 * The wallpaper gallery's LAN upload sheet: a QR code, one line of title, the browser address and a status
 * dot, on the gallery's dark smoked glass. The address carries the receiver's key, so there is no code to read
 * out. Owns the receiver, which stops when the sheet closes. Uploads arrive on the main thread.
 */
final class LanTransferDialog {
    interface Listener { void uploaded(File file, String mime); }

    private static final int INK_LIGHT = 0xFFF5F7FA;
    private static final int INK_MUTED = 0x8CDCE6F2;
    private static final int INK_SOFT = 0xD9FFFFFF;
    private static final int ACCENT = 0xFF8BEFF2;
    private static final int EMERALD = 0xFF4CD98B;
    private static final int WARM = 0xFFFF8A80;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LanWallpaperReceiver receiver;
    private Dialog dialog;
    private TextView status;
    private View dot;
    private android.animation.ValueAnimator pulse;

    private LanTransferDialog(Activity activity) { this.activity = activity; }

    /** Opens the sheet, or shows why it cannot (no LAN address) and returns null. */
    static LanTransferDialog show(Activity activity, Typeface medium, Typeface regular, Listener listener) {
        LanTransferDialog sheet = new LanTransferDialog(activity);
        String address = localAddress();
        if (address == null) {
            Toast.makeText(activity, R.string.wallpaper_lan_unavailable, Toast.LENGTH_LONG).show();
            return null;
        }
        try {
            sheet.receiver = new LanWallpaperReceiver(address, LanWallpaperReceiver.newToken(new SecureRandom()),
                    activity.getCacheDir(), (file, mime) -> sheet.main.post(() -> {
                        if (!sheet.isShowing()) { file.delete(); return; }
                        listener.uploaded(file, mime);
                    }));
            sheet.build(medium, regular);
            return sheet;
        } catch (IOException | WriterException | RuntimeException | OutOfMemoryError failure) {
            sheet.dismiss();
            Toast.makeText(activity, R.string.wallpaper_lan_unavailable, Toast.LENGTH_LONG).show();
            return null;
        }
    }

    boolean isShowing() { return dialog != null && dialog.isShowing(); }

    /** Waiting pulses softly; receiving holds the accent; added turns green; a failure turns warm. */
    void status(int message) {
        if (status == null) return;
        status.setText(message);
        boolean waiting = message == R.string.wallpaper_lan_waiting;
        int color = message == R.string.wallpaper_lan_added ? EMERALD
                : message == R.string.wallpaper_lan_import_failed ? WARM : ACCENT;
        dot.setBackground(circle(color));
        status.setTextColor(waiting ? INK_MUTED : INK_SOFT);
        if (pulse != null) {
            if (waiting) { if (!pulse.isStarted()) pulse.start(); }
            else { pulse.cancel(); dot.setAlpha(1f); }
        }
    }

    void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        stop();
    }

    private void stop() {
        if (pulse != null) pulse.cancel();
        pulse = null;
        if (receiver != null) receiver.close();
        receiver = null;
        dialog = null;
        status = null;
    }

    private void build(Typeface medium, Typeface regular) throws WriterException {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(34), dp(34), dp(40), dp(34));
        body.setBackground(QuickSettingsGlass.dialogSurface(28f * density, Math.max(1f, density)));

        // White quiet zone around the code so phone cameras read it on any TV panel.
        FrameLayout qrFrame = new FrameLayout(activity);
        qrFrame.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable qrSurface = new GradientDrawable();
        qrSurface.setColor(Color.WHITE);
        qrSurface.setCornerRadius(dp(20));
        qrFrame.setBackground(qrSurface);
        ImageView qr = new ImageView(activity);
        qr.setImageBitmap(qrCode(receiver.url()));
        qr.setContentDescription(activity.getString(R.string.wallpaper_lan_title));
        qrFrame.addView(qr, new FrameLayout.LayoutParams(dp(176), dp(176)));
        body.addView(qrFrame);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        detailsParams.setMarginStart(dp(36));
        body.addView(details, detailsParams);

        TextView title = text(activity.getString(R.string.wallpaper_lan_title), 26, INK_LIGHT, medium);
        details.addView(title);
        details.addView(text(activity.getString(R.string.wallpaper_lan_browser), 13.5f, INK_MUTED, regular),
                margins(14));
        TextView url = text(receiver.url().substring("http://".length()), 16, INK_SOFT, regular);
        if (android.os.Build.VERSION.SDK_INT >= 21) url.setLetterSpacing(0.02f);
        details.addView(url, margins(4));

        LinearLayout state = new LinearLayout(activity);
        state.setOrientation(LinearLayout.HORIZONTAL);
        state.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(activity);
        dot.setBackground(circle(ACCENT));
        state.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        status = text(activity.getString(R.string.wallpaper_lan_waiting), 13.5f, INK_MUTED, regular);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.setMarginStart(dp(10));
        state.addView(status, statusParams);
        details.addView(state, margins(34));

        pulse = android.animation.ValueAnimator.ofFloat(1f, 0.25f);
        pulse.setDuration(1100);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.addUpdateListener(value -> { if (dot != null) dot.setAlpha((float) value.getAnimatedValue()); });
        pulse.start();

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(body);
        dialog.setOnDismissListener(closed -> stop());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(dp(620), activity.getResources().getDisplayMetrics().widthPixels - dp(48)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.66f);
        }
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }

    private TextView text(String value, float sp, int color, Typeface typeface) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout.LayoutParams margins(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static Bitmap qrCode(String value) throws WriterException {
        // The white frame is the quiet zone; one module of margin keeps the code large and crisp inside it.
        java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
        BitMatrix bits = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 352, 352, hints);
        Bitmap bitmap = Bitmap.createBitmap(bits.getWidth(), bits.getHeight(), Bitmap.Config.RGB_565);
        for (int y = 0; y < bits.getHeight(); y++)
            for (int x = 0; x < bits.getWidth(); x++)
                bitmap.setPixel(x, y, bits.get(x, y) ? Color.BLACK : Color.WHITE);
        return bitmap;
    }

    /** The TV's private IPv4 address on Wi-Fi or Ethernet, else on any other LAN interface. */
    private static String localAddress() {
        try {
            String fallback = null;
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = nic.getName().toLowerCase(Locale.ROOT);
                if (!nic.isUp() || nic.isLoopback() || name.startsWith("tun") || name.startsWith("ppp")
                        || name.startsWith("rmnet")) continue;
                for (InetAddress address : Collections.list(nic.getInetAddresses()))
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en"))
                            return address.getHostAddress();
                        if (fallback == null) fallback = address.getHostAddress();
                    }
            }
            return fallback;
        } catch (Exception ignored) {
            return null;
        }
    }
}
