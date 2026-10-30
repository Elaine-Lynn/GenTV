package com.gentv.care

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AdbShellTest {
    @Test fun exactSerialAndCommandArePassedAsSeparateArguments() = runTest {
        val directory = Files.createTempDirectory("gentv-adb-test-").toFile()
        val recorder = File(directory, "arguments.txt")
        val executable = File(directory, "adb-fixture")
        executable.writeText("""#!/bin/sh
printf '%s\n' "${'$'}@" > '${recorder.absolutePath}'
if [ "${'$'}3" = get-state ]; then printf 'device\n'; else printf 'fixture-output\n'; fi
""")
        executable.setExecutable(true)
        val transport = AdbShell(executable.absolutePath, "fixture:1234")
        transport.requireAuthorised()
        val command = "settings get secure default_input_method"
        assertEquals("fixture-output\n", transport.run(command))
        assertEquals(listOf("-s", "fixture:1234", "shell", command), recorder.readLines())
    }
    @Test fun aNonPngCaptureIsRejected() = runTest {
        val executable = File.createTempFile("gentv-adb-fixture", ".sh")
        executable.writeText("#!/bin/sh\nprintf 'not-a-screenshot\\n'\n")
        executable.setExecutable(true)
        try {
            try { AdbShell(executable.absolutePath, "fixture").screenshot(); fail("invalid image accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { executable.delete() }
    }
    @Test fun missingOrOptionLikeTransportIsRejected() {
        for (serial in listOf("", "-s", "other device")) {
            try { AdbShell("adb", serial); fail("invalid transport accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
