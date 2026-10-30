package com.gentv.care

import com.gentv.core.ops.*
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecoveryTest {
    @Test fun unavailableConfigBridgeDoesNotBlockPackageAndDnsRecovery() = runTest {
        val fake = FakeTv.googleTv().apply {
            props["ro.build.fingerprint"] = "test-fingerprint"
            props["ro.serialno"] = "test-device"
            installed += "com.example.ads"
        }
        val tv = object : TvShell by fake {
            override suspend fun run(command: String): String = when {
                command.startsWith("settings list ") -> "test=1\n" + fake.run(command)
                command.startsWith("dumpsys package ") -> "User 0: installed=true enabled=1"
                else -> fake.run(command)
            }
        }
        val directory = File(Files.createTempDirectory("gentv-recovery-").toFile(), "session")
        val session = Session(directory); session.create(tv)
        session.rememberStates(tv, listOf("com.example.ads"))
        session.log.append(ActionRecord(1, Op.DISABLE, "com.example.ads", Phase.INTENT))
        session.log.append(ActionRecord(2, Op.SETTING, "global/private_dns_mode", Phase.INTENT, before = null))
        fake.disabled += "com.example.ads"
        fake.settings["global/private_dns_mode"] = "hostname"
        privateWrite(File(directory, "desktop-original.json"), "{\"config\":{\"schema\":2}}")
        val options = Options(arrayOf("restore", "--session", directory.absolutePath, "--apply"))
        val result = execute(options, tv, File(System.getProperty("gentv.repo")))
        assertEquals("partial", result["status"]!!.jsonPrimitive.contentOrNull)
        assertFalse("com.example.ads" in fake.disabled)
        assertFalse("global/private_dns_mode" in fake.settings)
    }
    @Test fun stateReplacementRetainsPrivateCompleteFiles() {
        val directory = Files.createTempDirectory("gentv-state-").toFile()
        val state = File(directory, "state.json")
        privateWrite(state, "{\"before\":0}")
        privateWrite(state, "{\"before\":0,\"next\":1}")
        assertEquals("{\"before\":0,\"next\":1}", state.readText())
        assertEquals(listOf("state.json"), directory.listFiles()!!.map { it.name })
    }
}
