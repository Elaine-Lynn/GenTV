package com.gentv.tv;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class MemoryCleanupTest {
    @Test public void memoryFluctuationWithoutProcessExitsIsNotReportedAsFreed() {
        assertEquals(0, MemoryCleanup.reclaimedMb(0, 20 * 1024L * 1024L, 0));
        assertEquals(0, MemoryCleanup.reclaimedMb(20 * 1024L * 1024L, 0, 2));
        assertEquals(7, MemoryCleanup.reclaimedMb(0, 7 * 1024L * 1024L + 123, 1));
    }
    @Test public void snapshotsExcludeSystemAndMalformedProcesses() {
        Set<String> snapshot = MemoryCleanup.appProcesses("USER PID NAME\n"
                + "system 12 system_server\nroot 10 init\nshell 15 sh\n"
                + "u0_a123 777 com.example.video\nu10_a45 888 com.example.app:worker\n"
                + "u0_a123 invalid broken\nu0_a123 777 com.example.video\n");
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.contains("777:com.example.video"));
        assertTrue(snapshot.contains("888:com.example.app:worker"));
    }

    @Test public void exitedProcessCountHandlesPidReuseAndSurvivingApps() {
        Set<String> before = MemoryCleanup.appProcesses("u0_a1 20 app.one\nu0_a2 30 app.two\n");
        before.removeAll(MemoryCleanup.appProcesses("u0_a3 20 app.new\nu0_a2 30 app.two\n"));
        assertEquals(1, before.size());
        assertTrue(before.contains("20:app.one"));
    }
}
