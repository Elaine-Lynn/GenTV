package com.gentv.mobile

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import com.gentv.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SelfUpdateRestoreTest {
    @get:org.junit.Rule val configuredApi = ConfiguredApi()
    private fun fixture(): File {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(app.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val data = "verified update fixture".toByteArray()
        val apk = File(directory, "update.apk").apply { writeBytes(data) }
        val info = UpdateInfo("1.0.0", "https://api.gentv.app/tv/app.apk", 99,
            MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }, data.size.toLong())
        File(directory, "pending.json").writeText(Json.encodeToString(UpdateInfo.serializer(), info))
        fun pkg(code: Int) = PackageInfo().apply {
            packageName = app.packageName; versionName = "1.0.0"; longVersionCode = code.toLong()
            applicationInfo = ApplicationInfo().apply { packageName = app.packageName; minSdkVersion = 26 }
            signingInfo = SigningInfo().also { shadowOf(it).setSignatures(arrayOf(Signature("abcd"))) }
        }
        shadowOf(app.packageManager).installPackage(pkg(11))
        shadowOf(app.packageManager).setPackageArchiveInfo(apk.path, pkg(99))
        return directory
    }
    private fun waitForEnd(updater: SelfUpdate) {
        val until = System.nanoTime() + 5_000_000_000L
        while (updater.status.value == InstallStatus.Verifying && System.nanoTime() < until) Thread.sleep(10)
    }
    @Test fun recreatedUpdaterRevalidatesAndRestoresReadyApk() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val directory = fixture()
        try {
            val updater = SelfUpdate(RuntimeEnvironment.getApplication())
            waitForEnd(updater)
            assertTrue(updater.status.value.toString(), updater.status.value is InstallStatus.Ready)
            updater.cancel()
            assertFalse(File(directory, "pending.json").exists())
        } finally { directory.deleteRecursively(); Dispatchers.resetMain() }
    }
    @Test fun changedCachedApkIsDiscardedInsteadOfOffered() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val directory = fixture()
        File(directory, "update.apk").appendText("tampered")
        try {
            val updater = SelfUpdate(RuntimeEnvironment.getApplication())
            waitForEnd(updater)
            assertEquals(InstallStatus.Idle, updater.status.value)
            assertFalse(File(directory, "pending.json").exists())
            assertFalse(File(directory, "update.apk").exists())
        } finally { directory.deleteRecursively(); Dispatchers.resetMain() }
    }
}
