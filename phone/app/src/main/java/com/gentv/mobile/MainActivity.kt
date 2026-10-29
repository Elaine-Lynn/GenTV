package com.gentv.mobile

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.gentv.mobile.data.Analytics
import com.gentv.mobile.data.Analytics.Event
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import com.gentv.mobile.data.REPO
import com.gentv.mobile.data.ShareCard
import com.gentv.mobile.data.Store
import com.gentv.mobile.data.GenTVFeedback
import com.gentv.mobile.data.caption
import com.gentv.mobile.ui.Host
import com.gentv.mobile.data.Trace
import com.gentv.mobile.net.AdbBackend
import com.gentv.mobile.net.DemoBackend
import com.gentv.mobile.net.Discovery
import com.gentv.mobile.ui.GenTVApp
import com.gentv.mobile.ui.GenTVTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val analyticsPreferences by lazy { getSharedPreferences("gentv_metrics", MODE_PRIVATE) }
    private val analytics by lazy {
        Analytics(kotlinx.coroutines.CoroutineScope(lifecycleScope.coroutineContext + kotlinx.coroutines.Dispatchers.IO),
            BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, Analytics::post).also {
            it.setEnabled(Analytics.configured && analyticsPreferences.getBoolean("enabled", false))
        }
    }

    private fun observeMetrics() {
        if (BuildConfig.DEBUG) return // Emulator and development traffic never enters product metrics.
        lifecycleScope.launch {
            var previous: UiState? = null
            vm.ui.collect { state ->
                val before = previous
                if (before?.wizard?.step != state.wizard.step || (before?.desktop == null) != (state.desktop == null)) {
                    analytics.record(Event.SCREEN_VIEW, if (state.desktop != null) "desktop" else state.wizard.step.name.lowercase(java.util.Locale.ROOT))
                }
                if (before != null && before.connect::class != state.connect::class) {
                    val result = when (state.connect) {
                        is com.gentv.core.flow.ConnectState.Connected -> "connected"
                        is com.gentv.core.flow.ConnectState.Denied -> "denied"
                        is com.gentv.core.flow.ConnectState.Unreachable -> "unreachable"
                        is com.gentv.core.flow.ConnectState.Failed -> "failed"
                        else -> null
                    }
                    result?.let { analytics.record(Event.CONNECTION_RESULT, it) }
                }
                if (before?.tidy?.finished == false && state.tidy.finished) analytics.record(Event.TIDY_COMPLETE)
                if (before != null) state.files.items.forEach { item ->
                    val old = before.files.items.firstOrNull { it.id == item.id }
                    if (old != null && old.status != item.status) analytics.record(Event.FILE_SEND_RESULT, item.status.name.lowercase(java.util.Locale.ROOT))
                }
                if (before != null && before.settingsUpdate != state.settingsUpdate) {
                    val result = when (state.settingsUpdate) {
                        com.gentv.mobile.data.UpdateStatus.Current -> "current"
                        is com.gentv.mobile.data.UpdateStatus.Available -> "available"
                        com.gentv.mobile.data.UpdateStatus.Failed -> "failed"
                        com.gentv.mobile.data.UpdateStatus.Unconfigured -> "unconfigured"
                        else -> null
                    }
                    result?.let { analytics.record(Event.UPDATE_CHECK, it) }
                }
                previous = state
            }
        }
    }

    private val selfUpdate: com.gentv.mobile.data.SelfUpdate by viewModels()


    private val vm: WizardViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application
                val store = Store(app.filesDir)
                val trace = Trace(store.logsDir)
                val real by lazy { AdbBackend(Discovery(app, trace::line), store.keyFile, trace::line) }
                WizardViewModel(
                    store = store,
                    trace = trace,
                    real = { real },
                    demo = if (BuildConfig.DEBUG) DemoBackend(phoneApk = { pkg ->
                        runCatching { java.io.File(packageManager.getApplicationInfo(pkg, 0).sourceDir) }.getOrNull()
                    }) else null,
                    lists = { Store.loadLists(app.assets) },
                    desktopPreviewApk = { runCatching {
                        val bytes = app.assets.open("launcher/gentv-home.apk").use { it.readBytes() }
                        val manifest = org.json.JSONObject(app.assets.open("launcher/build-manifest.json").bufferedReader().use { it.readText() })
                        com.gentv.mobile.data.DesktopPreviewApk(bytes, manifest.getJSONObject("sha256").getString("gentv-home.apk"))
                    }.getOrNull() },
                    launcherApk = { home -> runCatching { app.assets.open("launcher/${home.asset}").use { it.readBytes() } }.getOrNull() },
                    iconFromApk = com.gentv.mobile.data.ApkIcons(app, store)::iconFromApk,
                    labelFromApk = com.gentv.mobile.data.ApkIcons(app, store)::labelFromApk,
                    updates = if (BuildConfig.UPDATE_API.isBlank() || !com.gentv.mobile.data.UpdateCheck.needed(installerOf(app.packageName))) null
                        else com.gentv.mobile.data.UpdateCheck(store.updateFile, BuildConfig.VERSION_NAME, ::httpGet),
                    looks = { id -> com.gentv.mobile.data.Looks.load(app.assets, id) },
                    photoWallpaper = { f -> com.gentv.mobile.data.Wallpaper.fromPhoto(f.size) { f.open() } },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG && savedInstanceState == null) analytics.record(Event.APP_OPEN)
        observeMetrics()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!vm.back()) finish() }
        })
        setContent {
            GenTVTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.gentv.mobile.ui.LocalAppLanguage provides com.gentv.mobile.ui.AppLanguage.from(state.languageCode),
                    com.gentv.mobile.ui.LocalChangeLanguage provides { vm.changeLanguage(it.code) },
                ) { GenTVApp(state, vm, ::shareLogs, host) }
            }
        }
    }

    /** While the remote is open, the phone's volume buttons turn the TV's volume. */
    // Retain upstream hardware-volume handling; AndroidX core marks this inherited hook restricted.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val up = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        if ((up || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) && vm.ui.value.wizard.step == com.gentv.core.flow.Step.REMOTE) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) vm.press(if (up) com.gentv.core.ops.RemoteKey.VOLUME_UP else com.gentv.core.ops.RemoteKey.VOLUME_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private val feedback by lazy {
        GenTVFeedback(
            version = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            os = "Android ${Build.VERSION.RELEASE}",
            userId = installId(),
        )
    }

    // Lazy: filesDir is null until the activity's context is attached, so it can't be read in an initializer.
    private val host by lazy { Host(
        sendFeedback = { draft, includeLog ->
            val log = if (includeLog) runCatching { vm.exportLogs(File(cacheDir, "share"), BuildConfig.VERSION_NAME).readText() }.getOrNull() else null
            val result = if (GenTVFeedback.ENDPOINT.isBlank()) false else feedback.send(draft.copy(log = log))
            if (!BuildConfig.DEBUG) analytics.record(Event.FEEDBACK_SUBMIT, if (GenTVFeedback.ENDPOINT.isBlank()) "unconfigured" else if (result) "success" else "failed")
            result
        },
        renderCard = { ShareCard(this, vm.ui.value.icons).render(it) },
        shareCard = { stats ->
            val file = ShareCard(this, vm.ui.value.icons).save(stats, File(cacheDir, "share"))
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, stats.caption())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share your GenTV card"))
        },
        openStar = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO))) } },
        openUrl = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
        rating = com.gentv.mobile.data.RatingStore(File(filesDir, "rating.json")),
        requestReview = ::requestReview,
        selfUpdate = selfUpdate,
        installUpdate = { selfUpdate.install(this) },
        analyticsEnabled = { analyticsPreferences.getBoolean("enabled", false) },
        setAnalyticsEnabled = { enabled ->
            analyticsPreferences.edit().putBoolean("enabled", enabled).apply()
            analytics.setEnabled(enabled && !BuildConfig.DEBUG && Analytics.configured)
        },
        recordScreen = { if (!BuildConfig.DEBUG) analytics.record(Event.SCREEN_VIEW, it) },
    ) }

    /** Opens the official GenTV website from the satisfaction prompt. */
    private fun requestReview() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.gentv.mobile.data.SITE))) }
    }

    /** A random id for this install, so the feedback service can group messages from one install. No account, no device id. */
    private fun installId(): String {
        val f = File(filesDir, "install-id")
        return runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: java.util.UUID.randomUUID().toString().also { runCatching { f.writeText(it) } }
    }

    /** Which app installed GenTV ("com.android.vending" for Google Play); null when Android won't say. */
    private fun installerOf(pkg: String): String? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(pkg)
    }.getOrNull()

    /** A small GET for the update check; null on any error. */
    private fun httpGet(url: String): String? = runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "GenTV/${BuildConfig.VERSION_NAME}")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(64 * 1024 + 1)
                var count = 0
                while (count < buffer.size) { val n = reader.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                if (count > 64 * 1024) null else String(buffer, 0, count)
            }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun shareLogs() {
        val file = vm.exportLogs(File(cacheDir, "share"), BuildConfig.VERSION_NAME)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GenTV log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share logs"))
    }
}
