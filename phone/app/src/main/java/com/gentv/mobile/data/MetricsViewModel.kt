package com.gentv.mobile.data

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gentv.mobile.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.UUID

/** Keep the session and queue through Activity recreation; IDs remain app-private. */
class MetricsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences=application.getSharedPreferences("gentv_metrics", android.content.Context.MODE_PRIVATE)
    val analytics=Analytics(
        CoroutineScope(viewModelScope.coroutineContext+Dispatchers.IO),
        BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, Analytics::post,
        installation={
            preferences.getString("install_v2",null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("install_v2",it).apply()
            }
        },
        macAddress={ DeviceMac.read() },
        traffic={ if (BuildConfig.DEBUG || Build.FINGERPRINT.startsWith("generic") || Build.PRODUCT.contains("sdk") || preferences.getBoolean("test_traffic",false)) "test" else "production" },
    ).also { it.setEnabled(!BuildConfig.DEBUG && Analytics.configured && preferences.getBoolean("enabled_v3",false)) }
}
