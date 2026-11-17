package com.gentv.care

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/** Bounded Android binary XML reader for literal manifest package and minSdkVersion. */
object ApkMetadata {
    data class Identity(val packageName: String, val minSdk: Int)
    fun read(file: File): Identity = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().filter { it.name == "AndroidManifest.xml" }.toList()
        require(entries.size == 1) { "APK must have exactly one manifest" }
        val bytes = zip.getInputStream(entries.single()).use { it.readNBytes(4 * 1024 * 1024 + 1) }
        require(bytes.size <= 4 * 1024 * 1024) { "Manifest too large" }
        parse(bytes)
    }
    fun parse(bytes: ByteArray): Identity {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun range(at: Int, count: Int) { require(at >= 0 && count >= 0 && at.toLong() + count <= bytes.size) { "Malformed binary manifest" } }
        fun u16(at: Int): Int { range(at, 2); return b.getShort(at).toInt() and 65535 }
        fun i32(at: Int): Int { range(at, 4); return b.getInt(at) }
        require(u16(0) == 3 && u16(2) == 8 && i32(4) == bytes.size) { "Expected Android binary XML" }
        var strings: List<String>? = null
        var pkg: String? = null
        var sdk: Int? = null
        var at = 8
        var depth = 0
        var sawRoot = false
        fun str(index: Int) = requireNotNull(strings).getOrNull(index) ?: error("Invalid string index")
        while (at < bytes.size) {
            val type = u16(at); val header = u16(at + 2); val size = i32(at + 4)
            require(header >= 8 && size >= header); range(at, size)
            val end = at + size
            if (type == 1) {
                require(strings == null && header >= 28)
                val count = i32(at + 8); val flags = i32(at + 16); val start = i32(at + 20)
                require(count in 0..100000 && header.toLong() + count * 4L <= size && start in header..size)
                strings = (0 until count).map { index ->
                    val offset = i32(at + header + index * 4)
                    require(offset >= 0 && start.toLong() + offset < size)
                    var p = at + start + offset
                    fun byte(): Int { require(p < end); return bytes[p++].toInt() and 255 }
                    fun word(): Int { require(p + 2 <= end); val value = u16(p); p += 2; return value }
                    if (flags and 256 != 0) {
                        fun length(): Int { val v = byte(); return if (v and 128 != 0) ((v and 127) shl 8) or byte() else v }
                        length(); val length = length(); require(p.toLong() + length + 1 <= end)
                        require(bytes[p + length] == 0.toByte()); String(bytes, p, length, Charsets.UTF_8)
                    } else {
                        val v = word(); val length = if (v and 32768 != 0) ((v and 32767) shl 16) or word() else v
                        require(length >= 0 && p.toLong() + length * 2L + 2 <= end)
                        require(u16(p + length * 2) == 0); String(bytes, p, length * 2, Charsets.UTF_16LE)
                    }
                }
            } else if (type == 0x102) {
                require(header == 16 && size >= 36)
                val name = str(i32(at + 20))
                if (depth == 0) { require(!sawRoot && name == "manifest"); sawRoot = true }
                val attrStart = u16(at + 24); val attrSize = u16(at + 26); val count = u16(at + 28)
                require(attrStart >= 20 && attrSize >= 20 && 16L + attrStart + count.toLong() * attrSize <= size)
                repeat(count) { index ->
                    val p = at + 16 + attrStart + index * attrSize
                    val ns = i32(p); val key = str(i32(p + 4)); val raw = i32(p + 8)
                    require(u16(p + 12) == 8)
                    val kind = bytes[p + 15].toInt() and 255; val value = i32(p + 16)
                    fun literal() = if (raw >= 0) str(raw) else if (kind == 3) str(value) else error("Manifest string must be literal")
                    if (depth == 0 && key == "package" && ns == -1) { require(pkg == null); pkg = literal() }
                    if (depth == 1 && name == "uses-sdk" && key == "minSdkVersion" && ns >= 0 && str(ns) == "http://schemas.android.com/apk/res/android") {
                        require(sdk == null)
                        sdk = if (kind == 16 || kind == 17) value else literal().toIntOrNull() ?: error("Preview/resource minSdk unsupported")
                    }
                }
                depth++
            } else if (type == 0x103) {
                require(header == 16 && size >= 24 && depth > 0); depth--
            }
            at = end
        }
        require(sawRoot && depth == 0 && pkg != null && sdk != null && sdk >= 1) { "Missing package/minSdk or incomplete manifest" }
        return Identity(pkg, sdk)
    }
}
