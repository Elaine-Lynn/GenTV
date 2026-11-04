package com.gentv.tv;

import java.net.NetworkInterface;
import java.util.Locale;

/** Best-effort factory MAC access; never uses root, shell commands or Wi-Fi BSSID. */
public final class DeviceMac {
    private DeviceMac() {}
    public static String normalize(String value) {
        if (value == null) return "";
        String hex = value.trim().replace(":", "").replace("-", "").toLowerCase(Locale.ROOT);
        if (!hex.matches("[0-9a-f]{12}") || hex.equals("000000000000")) return "";
        // Reject multicast and locally administered/randomized addresses, including
        // Android's shared 02:00:00:00:00:00 placeholder.
        if ((Integer.parseInt(hex.substring(0, 2), 16) & 3) != 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) out.append(':');
            out.append(hex, i, i + 2);
        }
        return out.toString();
    }
    public static String read() {
        // Stable preference independent of which interface currently has a link.
        for (String name : new String[] {"wlan0", "eth0"}) {
            try {
                NetworkInterface network = NetworkInterface.getByName(name);
                if (network == null) continue;
                byte[] bytes = network.getHardwareAddress();
                if (bytes == null || bytes.length != 6) continue;
                StringBuilder raw = new StringBuilder();
                for (byte b : bytes) raw.append(String.format(Locale.ROOT, "%02x", b & 255));
                String mac = normalize(raw.toString());
                if (!mac.isEmpty()) return mac;
            } catch (java.net.SocketException | SecurityException ignored) {
                // Normal for non-system apps on modern Android: use installation ID.
            }
        }
        return "";
    }
}
