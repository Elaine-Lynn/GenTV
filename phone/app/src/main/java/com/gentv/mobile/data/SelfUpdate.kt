package com.gentv.mobile.data

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface InstallStatus {
    data object Idle : InstallStatus
    data class Downloading(val percent: Int) : InstallStatus
    data class Ready(val info: UpdateInfo, val file: File) : InstallStatus
    data object Verifying : InstallStatus
    data object PermissionRequired : InstallStatus
    data object InstallerOpened : InstallStatus
    data object Failed : InstallStatus
}

class SelfUpdate(app: Application) : AndroidViewModel(app) {
    private val state = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val status = state.asStateFlow()
    internal var transfer: (UpdateInfo, File, (Long) -> Unit, () -> Unit) -> Unit = UpdateDownload::download
    private var job: Job? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private var lastInfo: UpdateInfo? = null
    fun retry() { lastInfo?.let(::download) }
    private var ready: InstallStatus.Ready? = null
    private val directory get() = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }

    private val pendingMetadata get() = File(directory, "pending.json")

    init {
        val pending = File(app.cacheDir, "updates/pending.json")
        if (pending.isFile) {
            val attempt = generation.incrementAndGet()
            state.value = InstallStatus.Verifying
            job = viewModelScope.launch {
                val apk = File(directory, "update.apk")
                try {
                    val info = withContext(Dispatchers.IO) {
                        require(pending.length() in 1..8192)
                        val cached = kotlinx.serialization.json.Json.decodeFromString(UpdateInfo.serializer(), pending.readText())
                        UpdateDownload.verify(apk, cached)
                        validate(apk, cached)
                        cached
                    }
                    lastInfo = info
                    ready = InstallStatus.Ready(info, apk)
                    state.value = ready!!
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    if (generation.get() == attempt) { pending.delete(); apk.delete(); state.value = InstallStatus.Idle }
                }
            }
        }
    }

    fun download(info: UpdateInfo) {
        if (job?.isCompleted == false) return
        val attempt = generation.incrementAndGet()
        lastInfo = info
        ready = null
        state.value = InstallStatus.Downloading(0)
        job = viewModelScope.launch {
            val file = File(directory, "update.apk")
            try {
                withContext(Dispatchers.IO) {
                    pendingMetadata.delete()
                    file.delete()
                    require(info.minSdk <= Build.VERSION.SDK_INT && directory.usableSpace > info.sizeBytes + 16L * 1024 * 1024)
                    transfer(info, file, { n -> ensureActive(); if (generation.get() == attempt) state.value = InstallStatus.Downloading((100 * n / info.sizeBytes).toInt()) }, { ensureActive() })
                    ensureActive()
                    if (generation.get() == attempt) state.value = InstallStatus.Verifying
                    validate(file, info)
                    ensureActive()
                    val temporary = File(directory, "pending.json.tmp")
                    temporary.writeText(kotlinx.serialization.json.Json.encodeToString(UpdateInfo.serializer(), info))
                    check(temporary.renameTo(pendingMetadata))
                }
                ready = InstallStatus.Ready(info, file)
                state.value = ready!!
            } catch (e: CancellationException) { file.delete(); if (generation.get() == attempt) state.value = InstallStatus.Idle; throw e }
            catch (_: Exception) { file.delete(); if (generation.get() == attempt) state.value = InstallStatus.Failed }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        pendingMetadata.delete()
        ready = null
        state.value = InstallStatus.Idle
    }

    /** Revalidate immediately before granting the installer read access. No silent installs. */
    fun install(activity: Activity) {
        if (job?.isCompleted == false) return
        val downloaded = ready ?: return
        state.value = InstallStatus.Verifying
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { UpdateDownload.verify(downloaded.file, downloaded.info); validate(downloaded.file, downloaded.info) }
                if (activity.isFinishing || activity.isDestroyed) { state.value = downloaded; return@launch }
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    state.value = InstallStatus.PermissionRequired
                } else {
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", downloaded.file)
                    activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    state.value = InstallStatus.InstallerOpened
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = InstallStatus.Failed }
        }
    }

    @Suppress("DEPRECATION")
    private fun validate(file: File, info: UpdateInfo) {
        val app = getApplication<Application>()
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
        val current = pm.getPackageInfo(app.packageName, flags)
        UpdatePackage.verify(archive, current, info, Build.VERSION.SDK_INT, app.packageName)
    }
}
