package com.gentv.care

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AdbShellTest {
    private fun python(): String {
        val command = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
        val process = ProcessBuilder(command, "-c", "import sys; print(sys.executable)").start()
        val path = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0 && path.isNotBlank())
        return path
    }
    @Test fun exactSerialAndCommandArePassedAsSeparateArguments() = runTest {
        val directory = Files.createTempDirectory("gentv-adb-test-").toFile()
        try {
            val recorder = File(directory, "arguments.txt")
            val executable = File(directory, "adb-fixture.py")
            val path = recorder.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
            executable.writeText("""import sys
from pathlib import Path
Path('$path').write_text('\n'.join(sys.argv[1:]) + '\n')
print('device' if sys.argv[3] == 'get-state' else 'fixture-output')
""")
            val transport = AdbShell(executable.absolutePath, "fixture:1234", python())
            transport.requireAuthorised()
            val command = "settings get secure default_input_method"
            assertEquals("fixture-output", transport.run(command).trim())
            assertEquals(listOf("-s", "fixture:1234", "shell", command), recorder.readLines())
        } finally { directory.deleteRecursively() }
    }
    @Test fun aNonPngCaptureIsRejected() = runTest {
        val executable = File.createTempFile("gentv-adb-fixture", ".py")
        executable.writeText("print('not-a-screenshot')\n")
        try {
            try { AdbShell(executable.absolutePath, "fixture", python()).screenshot(); fail("invalid image accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { executable.delete() }
    }
    @Test fun missingOrOptionLikeTransportIsRejected() {
        for (serial in listOf("", "-s", "other device")) {
            try { AdbShell("adb", serial); fail("invalid transport accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
