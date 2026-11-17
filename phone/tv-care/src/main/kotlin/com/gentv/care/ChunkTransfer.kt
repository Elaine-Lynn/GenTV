package com.gentv.care

import com.gentv.core.ops.TvShell
import java.util.Base64

/** Shared native/fallback upload. Uncertain appends resume only after offset and prefix hash agree. */
class ChunkTransfer(private val tv: TvShell, private val chunkSize: Int = 12_288) {
    private fun path(path: String): String {
        require(Regex("/data/local/tmp/gentv[-A-Za-z0-9_./]*").matches(path) && !path.split('/').contains("..")) { "Chunk transfer only stages GenTV temporary files" }
        return path
    }
    suspend fun hash(remote: String): String {
        val safe = path(remote)
        val output = tv.run("sha256sum $safe 2>/dev/null || toybox sha256sum $safe 2>/dev/null")
        return Regex("(?m)^([a-fA-F0-9]{64})\\s").find(output)?.groupValues?.get(1)?.lowercase()
            ?: error("Cannot verify staged file SHA-256; no install attempted")
    }
    suspend fun verify(bytes: ByteArray, remote: String) {
        check(hash(remote) == sha256(bytes)) { "Staged file SHA-256 differs; do not install it" }
    }
    suspend fun push(bytes: ByteArray, remote: String) {
        require(chunkSize in 1024..24_576)
        val target = path(remote)
        val part = "$target.part-${sha256(bytes).take(16)}"
        val count = tv.run("if [ -f $part ]; then wc -c < $part; else echo 0; fi").trim().toIntOrNull()
            ?: error("Cannot read partial upload size")
        require(count in 0..bytes.size) { "Partial upload exceeds source length" }
        if (count > 0) {
            val remotePrefix = hash(part)
            check(remotePrefix == sha256(bytes.copyOfRange(0, count))) { "Partial upload differs; preserve it for diagnosis, do not append" }
        } else tv.run(": > $part")
        var offset = count
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            val encoded = Base64.getEncoder().encodeToString(bytes.copyOfRange(offset, end))
            tv.run("printf '%s' '$encoded' | base64 -d >> $part")
            val observed = tv.run("wc -c < $part").trim().toIntOrNull()
            check(observed == end) { "Upload append uncertain; reconnect and resume after reading size/hash" }
            offset = end
        }
        verify(bytes, part)
        tv.run("mv $part $target")
        verify(bytes, target)
    }
}
