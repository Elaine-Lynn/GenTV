package com.gentv.mobile

import com.gentv.mobile.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class UpdateDownloadTest {
    @get:org.junit.Rule val configuredApi = ConfiguredApi()
    @get:Rule val tmp = TemporaryFolder()
    private val bytes = "test apk payload".toByteArray()
    private fun info() = UpdateInfo("1.0.0", "https://api.gentv.app/tv/releases/app.apk", 12,
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong())

    @Test fun onlyHttpsMobileApiDownloadsAreAllowed() {
        assertTrue(UpdateDownload.allowedUrl(info().url))
        listOf("http://api.gentv.app/mobile/a.apk", "https://evil.test/mobile/a.apk", "https://api.gentv.app.evil/mobile/a.apk",
            "https://api.gentv.app/tv/../a.apk", "https://api.gentv.app/tv/%2e%2e/a.apk", "https://user@api.gentv.app/mobile/a.apk",
            "https://api.gentv.app/other/a.apk").forEach { assertFalse(it, UpdateDownload.allowedUrl(it)) }
    }

    @Test fun buildsWithoutAnApiNeverDownload() {
        UpdateDownload.apiBase = ""
        assertFalse(UpdateDownload.allowedUrl(info().url))
        assertFalse(info().installable)
    }

    @Test fun completeDownloadChecksHashAndReportsProgress() {
        val file = tmp.newFile()
        var progress = 0L
        UpdateDownload.copy(bytes.inputStream(), file, info(), { progress = it }, {})
        assertArrayEquals(bytes, file.readBytes()); assertEquals(bytes.size.toLong(), progress)
    }

    @Test fun truncatedOversizedOrTamperedDataIsDeleted() {
        listOf(bytes.dropLast(1).toByteArray(), bytes + byteArrayOf(0), ByteArray(bytes.size)).forEach { input ->
            val file = tmp.newFile()
            assertThrows(IllegalArgumentException::class.java) { UpdateDownload.copy(input.inputStream(), file, info(), {}, {}) }
            assertFalse(file.exists())
        }
    }

    @Test fun cancellationRemovesPartialDownload() {
        val file = tmp.newFile()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            UpdateDownload.copy(bytes.inputStream(), file, info(), {}, { throw kotlinx.coroutines.CancellationException() })
        }
        assertFalse(file.exists())
    }

    @Test fun parsesMobileContractAndRejectsIncompleteOrUntrustedMetadata() {
        val body = """{"version":"1.0.0","version_code":12,"apk_url":"${info().url}","sha256":"${info().sha256}","size_bytes":${bytes.size},"min_sdk":26}"""
        assertEquals(info(), UpdateCheck.parse(body))
        assertNull(UpdateCheck.parse(body.replace("api.gentv.app", "evil.test")))
        assertNull(UpdateCheck.parse(body.replace(info().sha256, "missing")))
        assertNull(UpdateCheck.parse(body.replace("\"size_bytes\":${bytes.size}", "\"size_bytes\":0")))
    }
}
