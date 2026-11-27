package com.gentv.tv;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/** Shared LAN address and QR rendering for TV dialogs that receive content from a phone. */
final class TvLan {
    private TvLan() {}

    static String localAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            String fallback = null;
            for (NetworkInterface network : Collections.list(interfaces)) {
                String name = network.getName().toLowerCase(Locale.ROOT);
                if (!network.isUp() || network.isLoopback() || name.startsWith("tun")
                        || name.startsWith("ppp") || name.startsWith("rmnet")) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                        return address.getHostAddress();
                    }
                    if (fallback == null) fallback = address.getHostAddress();
                }
            }
            return fallback;
        } catch (SocketException | SecurityException ignored) {
            return null;
        }
    }

    static Bitmap qrCode(String value) throws WriterException {
        BitMatrix bits = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 320, 320);
        int width = bits.getWidth();
        int height = bits.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = bits.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);
    }
}
