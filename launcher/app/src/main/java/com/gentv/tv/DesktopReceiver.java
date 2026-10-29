package com.gentv.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Explicit ordered broadcasts, protected by WRITE_SECURE_SETTINGS in the manifest. */
public final class DesktopReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            String operation = intent.getStringExtra("operation");
            JSONObject response;
            if ("read".equals(operation)) response = DesktopStore.snapshot(context);
            else if ("undo".equals(operation)) response = DesktopStore.undo(context, intent.getIntExtra("revision", -1));
            else if ("save".equals(operation)) {
                String payload = intent.getStringExtra("payload");
                if (payload == null || payload.length() > 60000) throw new IllegalArgumentException("Invalid payload");
                JSONObject profile = new JSONObject(new String(Base64.decode(payload, Base64.NO_WRAP), StandardCharsets.UTF_8));
                response = DesktopStore.save(context, profile, intent.getIntExtra("revision", -1));
            } else throw new IllegalArgumentException("Unknown operation");
            setResultData("GENTV1:" + Base64.encodeToString(response.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            setResultCode(0);
        } catch (Exception e) {
            setResultCode(1); setResultData("GENTV_ERROR:Read the TV again; no change confirmed.");
            android.util.Log.w("GenTV.Desktop", "Desktop request refused: " + e.getClass().getSimpleName());
        }
    }
}
