package com.gentv.tv;

import android.content.Context;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** TV and network callback APIs are absent on Android 4.2; do not load this class there. */
@RequiresApi(21)
final class ModernCallbacks {
    private final MainActivity home;
    private final Handler handler;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        private boolean lastValidated;
        @Override public void onAvailable(@NonNull Network network) { refreshNetwork(); }
        @Override public void onLost(@NonNull Network network) { refreshNetwork(); }
        @Override public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
            boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            if (validated != lastValidated || home.compactHeaderIconsEnabled()) refreshNetwork();
            lastValidated = validated;
        }
    };
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network network) { refreshNetwork(); }
        @Override public void onLost(@NonNull Network network) { refreshNetwork(); }
    };
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String id) { home.runOnUiThread(home::updateInputsButton); }
        @Override public void onInputRemoved(String id) { home.runOnUiThread(home::updateInputsButton); }
        @Override public void onInputStateChanged(String id, int state) { home.runOnUiThread(home::updateInputsButton); }
        @Override public void onInputUpdated(String id) { home.runOnUiThread(home::updateInputsButton); }
    };
    private ConnectivityManager connectivity;
    private TvInputManager inputs;

    ModernCallbacks(MainActivity home, Handler handler) {
        this.home = home;
        this.handler = handler;
    }

    private void refreshNetwork() { handler.post(home::updateWifiLabel); }

    void start() {
        inputs = (TvInputManager) home.getSystemService(Context.TV_INPUT_SERVICE);
        if (inputs != null) inputs.registerCallback(inputCallback, handler);
        connectivity = CompatApi.service(home, ConnectivityManager.class);
        if (connectivity == null) return;
        if (Build.VERSION.SDK_INT >= 24) connectivity.registerDefaultNetworkCallback(networkCallback);
        else connectivity.registerNetworkCallback(new NetworkRequest.Builder().build(), networkCallback);
        if (Build.VERSION.SDK_INT >= 23) {
            connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
    }

    void stop() {
        if (inputs != null) inputs.unregisterCallback(inputCallback);
        if (connectivity != null) {
            connectivity.unregisterNetworkCallback(networkCallback);
            if (Build.VERSION.SDK_INT >= 23) connectivity.unregisterNetworkCallback(vpnCallback);
        }
    }
}
