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
 * The wallpaper gallery's LAN upload sheet in its dark smoked glass: a QR code, the browser address and the TV's
 * code, with a status line. Owns the receiver, which stops when the sheet closes. Uploads arrive on the main thread.
 */
final class LanTransferDialog {
    interface Listener { void uploaded(File file, String mime); }

    private static final int INK_LIGHT = 0xFFF5F7FA;
    private static final int INK_MUTED = 0x99DCE6F2;
    private static final int INK_SOFT = 0xC8DCE6F2;
    private static final int ACCENT = 0xFF8BEFF2;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LanWallpaperReceiver receiver;
    private Dialog dialog;
    private TextView status;

    private LanTransferDialog(Activity activity) { this.activity = activity; }

    /** Opens the sheet, or shows why it cannot (no LAN address) and returns null. */
    static LanTransferDialog show(Activity activity, Typeface medium, Typeface regular, Listener listener) {
        LanTransferDialog sheet = new LanTransferDialog(activity);
        String address = localAddress();
        if (address == null) {
            Toast.makeText(activity, R.string.wallpaper_lan_unavailable, Toast.LENGTH_LONG).show();
            return null;
        }
        String code = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10_000));
        try {
            sheet.receiver = new LanWallpaperReceiver(address, code, activity.getCacheDir(), (file, mime) ->
                    sheet.main.post(() -> {
                        if (!sheet.isShowing()) { file.delete(); return; }
                        listener.uploaded(file, mime);
                    }));
            sheet.build(code, medium, regular);
            return sheet;
        } catch (IOException | WriterException | RuntimeException | OutOfMemoryError failure) {
            sheet.dismiss();
            Toast.makeText(activity, R.string.wallpaper_lan_unavailable, Toast.LENGTH_LONG).show();
            return null;
        }
    }

    boolean isShowing() { return dialog != null && dialog.isShowing(); }

    void status(int message) { if (status != null) status.setText(message); }

    void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        stop();
    }

    private void stop() {
        if (receiver != null) receiver.close();
        receiver = null;
        dialog = null;
        status = null;
    }

    private void build(String code, Typeface medium, Typeface regular) throws WriterException {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(32), dp(28), dp(32), dp(26));
        body.setBackground(QuickSettingsGlass.dialogSurface(24f * density, Math.max(1f, density)));

        body.addView(text(activity.getString(R.string.wallpaper_lan_title), 24, INK_LIGHT, medium));
        body.addView(text(activity.getString(R.string.wallpaper_lan_subtitle), 14, INK_MUTED, regular),
                margins(8));

        LinearLayout columns = new LinearLayout(activity);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(columns, margins(24));

        // White quiet zone around the code so phone cameras read it on any TV panel.
        FrameLayout qrFrame = new FrameLayout(activity);
        qrFrame.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable qrSurface = new GradientDrawable();
        qrSurface.setColor(Color.WHITE);
        qrSurface.setCornerRadius(dp(16));
        qrFrame.setBackground(qrSurface);
        ImageView qr = new ImageView(activity);
        qr.setImageBitmap(qrCode(receiver.url() + "#code=" + code));
        qr.setContentDescription(activity.getString(R.string.wallpaper_lan_scan));
        qrFrame.addView(qr, new FrameLayout.LayoutParams(dp(184), dp(184)));
        columns.addView(qrFrame);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        detailsParams.setMarginStart(dp(28));
        columns.addView(details, detailsParams);

        details.addView(text(activity.getString(R.string.wallpaper_lan_scan), 18, INK_LIGHT, medium));
        details.addView(text(activity.getString(R.string.wallpaper_lan_browser), 13, INK_MUTED, regular),
                margins(18));
        TextView url = text(receiver.url(), 17, ACCENT, medium);
        url.setTextIsSelectable(true);
        details.addView(url, margins(5));
        details.addView(text(activity.getString(R.string.wallpaper_lan_code), 13, INK_MUTED, regular),
                margins(18));
        TextView codeView = text(code, 30, INK_LIGHT, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        if (android.os.Build.VERSION.SDK_INT >= 21) codeView.setLetterSpacing(0.14f);
        details.addView(codeView, margins(2));

        View divider = new View(activity);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
        dividerParams.topMargin = dp(22);
        body.addView(divider, dividerParams);

        body.addView(text(activity.getString(R.string.wallpaper_lan_limits), 12, INK_MUTED, regular), margins(14));
        status = text(activity.getString(R.string.wallpaper_lan_waiting), 14, INK_SOFT, regular);
        body.addView(status, margins(8));

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(body);
        dialog.setOnDismissListener(closed -> stop());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(dp(760), activity.getResources().getDisplayMetrics().widthPixels - dp(48)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.62f);
        }
    }

    private TextView text(String value, float sp, int color, Typeface typeface) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(typeface);
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
        BitMatrix bits = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 320, 320);
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
