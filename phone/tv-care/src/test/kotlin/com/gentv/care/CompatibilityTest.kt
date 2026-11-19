package com.gentv.care

import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CompatibilityTest {
    @Test fun api17TvCanInstallApi17Apk() {
        requireCompatibleTv(17, 17)
        requireCompatibleTv(22, 17)
        requireCompatibleTv(26, 26)
    }

    @Test fun olderTvOrNewerApkIsRejectedBeforeTransfer() {
        for ((device, apk) in listOf(16 to 17, 22 to 26)) {
            try {
                requireCompatibleTv(device, apk)
                fail("Accepted API $device device with API $apk APK")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun legacyHomeRequestDoesNotChangeTheDevice() = runTest {
        val tv = FakeTv.googleTv().apply { props["ro.build.version.sdk"] = "22" }
        val session = Session(File(Files.createTempDirectory("gentv-legacy-home-").toFile(), "session"))
        session.create(tv)
        val answer = execute(Options(arrayOf("home", "--session", session.directory.path, "--apply", "--ui-confirmed")), tv, File(System.getProperty("gentv.repo")))
        assertEquals("needs-device-action", answer["status"]?.toString()?.trim('"'))
        assertFalse(tv.commands.any { it.contains("set-home-activity") || it.startsWith("pm disable") })
    }
}
