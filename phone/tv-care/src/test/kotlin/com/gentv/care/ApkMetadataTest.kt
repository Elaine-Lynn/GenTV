package com.gentv.care
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream

class ApkMetadataTest {
    private fun fixture(pkg: String = "com.gentv.tv", sdk: Int = 26): ByteArray {
        fun buffer(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        val strings = listOf("manifest", "package", pkg, "uses-sdk", "minSdkVersion", "http://schemas.android.com/apk/res/android")
        val data = ByteArrayOutputStream(); val offsets = mutableListOf<Int>()
        strings.forEach { text -> offsets += data.size(); val bytes = text.toByteArray(); data.write(bytes.size); data.write(bytes.size); data.write(bytes); data.write(0) }
        val start = 28 + offsets.size * 4
        val pool = buffer(start + data.size()).apply {
            putShort(1); putShort(28); putInt(capacity()); putInt(strings.size); putInt(0); putInt(256); putInt(start); putInt(0)
            offsets.forEach { putInt(it) }; put(data.toByteArray())
        }.array()
        fun element(name: Int, ns: Int, key: Int, raw: Int, kind: Int, value: Int) = buffer(56).apply {
            putShort(0x102); putShort(16); putInt(56); putInt(1); putInt(-1)
            putInt(-1); putInt(name); putShort(20); putShort(20); putShort(1); putShort(0); putShort(0); putShort(0)
            putInt(ns); putInt(key); putInt(raw); putShort(8); put(0); put(kind.toByte()); putInt(value)
        }.array()
        fun end(name: Int) = buffer(24).apply { putShort(0x103); putShort(16); putInt(24); putInt(1); putInt(-1); putInt(-1); putInt(name) }.array()
        val body = pool + element(0,-1,1,2,3,2) + element(3,5,4,-1,16,sdk) + end(3) + end(0)
        return buffer(8).apply { putShort(3); putShort(8); putInt(body.size + 8) }.array() + body
    }
    @Test fun readsRealBuiltApkWhenSupplied() {
        val path = System.getenv("GENTV_TEST_APK") ?: return
        val metadata = ApkMetadata.read(java.io.File(path))
        assertEquals("com.gentv.tv", metadata.packageName)
        assertEquals(26, metadata.minSdk)
    }
    @Test fun readsLiteralIdentityWithoutSdkTools() { assertEquals(ApkMetadata.Identity("com.gentv.tv",26), ApkMetadata.parse(fixture())) }
    @Test fun retainsForeignPackageAndOldSdkForCallerRejection() { assertEquals(ApkMetadata.Identity("other.app",23), ApkMetadata.parse(fixture("other.app",23))) }
    @Test fun malformedSizesAndTruncationAreRejected() {
        for (bytes in listOf(fixture().dropLast(1).toByteArray(), fixture().apply { this[12] = -1; this[13] = -1; this[14] = -1; this[15] = 127 })) {
            try { ApkMetadata.parse(bytes); fail("malformed accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
