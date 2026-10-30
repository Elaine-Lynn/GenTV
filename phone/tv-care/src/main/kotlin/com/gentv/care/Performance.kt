package com.gentv.care

import com.gentv.core.ops.*
import com.gentv.core.lists.HomeApp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/** Same framestats/PSS definitions as tools/emulator_benchmark.py, applied to the installed real app. */
object Metrics {
    fun frames(output: String): List<Pair<Long, Long>> {
        var header: List<String>? = null
        val frames = mutableListOf<Pair<Long, Long>>()
        for (line in output.lineSequence()) {
            if (line.startsWith("Flags,")) { header = line.trim().split(','); continue }
            val names = header ?: continue
            val row = line.trim().split(',')
            fun number(key: String) = names.indexOf(key).takeIf { it >= 0 }?.let { row.getOrNull(it)?.toLongOrNull() }
            if (number("Flags") != 0L) continue
            val start = number("IntendedVsync") ?: continue
            val end = number("FrameCompleted") ?: continue
            if (start > 0 && end > start && end - start < 10_000_000_000L) frames += start to end
        }
        return frames.distinct()
    }
    fun percentile(values: List<Double>, fraction: Double): Double? = values.sorted().let {
        if (it.isEmpty()) null else if (fraction == .5) (it[(it.size-1)/2] + it[it.size/2])/2 else it[((it.size - 1) * fraction).toInt()]
    }
    fun cpu(output: String): Pair<Long, Long>? {
        val row = output.lineSequence().firstOrNull { it.startsWith("cpu ") }?.trim()?.split(Regex("\\s+"))?.drop(1)?.take(8)?.map { it.toLongOrNull() } ?: return null
        if (row.size < 4 || row.any { it == null }) return null
        return row.sumOf { it!! } to (row[3]!! + (row.getOrNull(4) ?: 0L))
    }
    fun busy(before: Pair<Long, Long>?, after: Pair<Long, Long>?): Double? {
        if (before == null || after == null) return null
        val total = after.first - before.first; val idle = after.second - before.second
        return if (total > 0 && idle in 0..total) 100.0 * (total - idle) / total else null
    }
}

class Performance(private val tv: TvShell, private val wait: suspend (Long) -> Unit = { delay(it) }) {
    suspend fun measure(samples: Int, idleSeconds: Int): JsonObject {
        require(samples in 3..20 && idleSeconds in 1..30)
        val pkg = HomeApp.GENTV.pkg
        require(pkg in Parse.packages(tv.run(Commands.LIST_PACKAGES))) { "Install GenTV before benchmarking it" }
        val homeBefore = Parse.component(tv.run(Commands.RESOLVE_HOME))
        val cold = mutableListOf<Double>(); val returns = mutableListOf<Double>(); val keys = mutableListOf<Double>()
        val evidence = mutableListOf<String>()
        val frameEvidence = mutableListOf<String>()
        tv.run("dumpsys gfxinfo $pkg reset")
        suspend fun keySample(key: String): Double? {
            val sent = tv.run("u=\$(cut -d ' ' -f1 /proc/uptime); echo GENTV_KEY_UPTIME=\$u; input keyevent $key")
            val timestamp = Regex("GENTV_KEY_UPTIME=([0-9.]+)").find(sent)?.groupValues?.get(1)?.toDoubleOrNull()?.times(1e9)?.toLong() ?: return null
            wait(300)
            val frame = Metrics.frames(tv.run("dumpsys gfxinfo $pkg framestats")).filter { it.second > timestamp }.minByOrNull { it.second } ?: return null
            return ((frame.second - timestamp) / 1e6).takeIf { it in 0.0..2000.0 }
        }
        try {
            repeat(samples) {
                tv.run("am force-stop $pkg")
                val launch = tv.run(Commands.startLauncher(HomeApp.GENTV.component, emptyList(), System.currentTimeMillis()/1000))
                evidence += LogRedactor.redact(launch.take(2000))
                if (launch.contains("Status: ok")) Regex("(?m)^TotalTime:\\s*(\\d+)").find(launch)?.groupValues?.get(1)?.toDoubleOrNull()?.let { cold += it }
                else error("GenTV launch failed during benchmark; no data cleared")
                wait(300)
                keySample("KEYCODE_DPAD_RIGHT")?.let { keys += it }
                if (homeBefore?.substringBefore('/') == pkg) {
                    tv.run(Commands.OPEN_SETTINGS); wait(300)
                    keySample("KEYCODE_HOME")?.let { returns += it }
                }
                frameEvidence += tv.run("dumpsys gfxinfo $pkg framestats").take(200000)
            }
            val frameText = tv.run("dumpsys gfxinfo $pkg framestats")
            val frames = (frameEvidence + frameText).flatMap { Metrics.frames(it) }.distinct()
            val memory = tv.run("dumpsys meminfo $pkg")
            val pss = (Regex("TOTAL PSS:\\s*(\\d+)").find(memory) ?: Regex("(?m)^\\s*TOTAL\\s+(\\d+)").find(memory))?.groupValues?.get(1)?.toDoubleOrNull()?.div(1024)
            val counts = frameEvidence.map { text -> Pair(Regex("Total frames rendered:.*?([0-9]+)").find(text)?.groupValues?.get(1)?.toLongOrNull(), Regex("Janky frames:.*?([0-9]+)").find(text)?.groupValues?.get(1)?.toLongOrNull()) }
            val totalFrames = counts.sumOf { it.first ?: 0L }
            val jank = if (counts.all { it.first != null && it.second != null } && totalFrames > 0) counts.sumOf { it.second!! } * 100.0 / totalFrames else null
            val first = Metrics.cpu(tv.run("cat /proc/stat")); wait(idleSeconds * 1000L)
            val busy = Metrics.busy(first, Metrics.cpu(tv.run("cat /proc/stat")))
            val props = Parse.props(tv.run(Commands.GETPROP))
            return buildJsonObject {
                put("kind", "performance"); put("schema", 1)
                val hardware = DeviceIdentity.hardware(props)
                put("deviceKey", if (hardware.isBlank()) JsonNull else JsonPrimitive(sha256((hardware + props["ro.build.fingerprint"].orEmpty()).toByteArray())))
                put("capturedAtUtc", java.time.Instant.now().toString())
                put("firmware", props["ro.build.fingerprint"].orEmpty()); put("model", props["ro.product.model"].orEmpty())
                put("summary", buildJsonObject {
                    put("requestedSamples", samples); put("coldSamples", cold.size); put("keySamples", keys.size); put("returnSamples", returns.size)
                    put("coldStartMsP50", Metrics.percentile(cold, .5)?.let(::JsonPrimitive) ?: JsonNull)
                    put("firstFrameAfterInjectedKeyMsP50", Metrics.percentile(keys, .5)?.let(::JsonPrimitive) ?: JsonNull)
                    put("firstFrameAfterHomeMsP50", Metrics.percentile(returns, .5)?.let(::JsonPrimitive) ?: JsonNull)
                    put("frameDurationMsP95", Metrics.percentile(frames.map { (it.second-it.first)/1e6 }, .95)?.let(::JsonPrimitive) ?: JsonNull)
                    put("jankPercent", jank?.let(::JsonPrimitive) ?: JsonNull); put("pssMb", pss?.let(::JsonPrimitive) ?: JsonNull)
                    put("deviceCpuBusyPercentDuringIdleWindow", busy?.let(::JsonPrimitive) ?: JsonNull)
                    put("idleSeconds", idleSeconds); put("frames", frames.size)
                })
                put("rawLaunchEvidence", buildJsonArray { evidence.forEach { add(it) } })
                put("rawFrameEvidence", buildJsonArray { frameEvidence.forEach { add(LogRedactor.redact(it)) } })
                put("limits", "Injected-key frame timing is a proxy, not physical remote latency; animation may affect it. CPU covers the whole TV. Missing metrics are null. No APK installation, data clear or default Home change. Return timing is omitted unless GenTV was already Home.")
            }
        } finally {
            // Restore whichever Home was selected; never change its preference to run a benchmark.
            if (homeBefore != null) tv.run(Commands.KEY_HOME)
        }
    }
}

fun comparePerformance(current: JsonObject, previous: JsonObject): JsonObject {
    require(current["deviceKey"] != null && current["deviceKey"] != JsonNull && current["firmware"]?.jsonPrimitive?.content?.isNotBlank() == true) { "Stable device/firmware evidence required for comparison" }
    require(current["kind"] == JsonPrimitive("performance") && current["kind"] == previous["kind"] && current["deviceKey"] == previous["deviceKey"] && current["firmware"] == previous["firmware"]) { "Compare only the same physical TV and firmware" }
    val old = previous["summary"]!!.jsonObject; val now = current["summary"]!!.jsonObject
    return buildJsonObject {
        for ((key, value) in now) {
            if (key.endsWith("Samples") || key == "frames" || key == "idleSeconds") continue
            val before = old[key]?.jsonPrimitive?.doubleOrNull; val after = value.jsonPrimitive.doubleOrNull
            put(key, if (before == null || after == null) JsonNull else JsonPrimitive(after-before))
        }
    }
}
