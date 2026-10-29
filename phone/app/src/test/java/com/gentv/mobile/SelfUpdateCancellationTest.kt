package com.gentv.mobile

import com.gentv.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SelfUpdateCancellationTest {
    @get:org.junit.Rule val configuredApi = ConfiguredApi()
    @Test fun blockedReadCannotReopenDialogAfterCancel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val updater = SelfUpdate(RuntimeEnvironment.getApplication())
        updater.transfer = { _, _, progress, _ ->
            entered.countDown()
            try { check(resume.await(3, TimeUnit.SECONDS)); progress(1) }
            finally { exited.countDown() }
        }
        try {
            updater.download(UpdateInfo("1.0.0", "https://api.gentv.app/tv/app.apk", 12, "a".repeat(64), 1))
            runCurrent()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            updater.cancel()
            assertEquals(InstallStatus.Idle, updater.status.value)
            resume.countDown()
            assertTrue(exited.await(3, TimeUnit.SECONDS))
            withContext(Dispatchers.IO) { delay(100) }
            runCurrent()
            assertEquals(InstallStatus.Idle, updater.status.value)
        } finally {
            resume.countDown(); updater.cancel(); Dispatchers.resetMain()
        }
    }
}
