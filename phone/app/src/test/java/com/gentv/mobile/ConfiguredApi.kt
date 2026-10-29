package com.gentv.mobile

import com.gentv.mobile.data.UpdateDownload
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Points update downloads at a GenTV API for one test, whatever API this build was configured with. */
class ConfiguredApi(private val base: String = "https://api.gentv.app/tv/") : TestWatcher() {
    private var previous = ""
    override fun starting(description: Description) { previous = UpdateDownload.apiBase; UpdateDownload.apiBase = base }
    override fun finished(description: Description) { UpdateDownload.apiBase = previous }
}
