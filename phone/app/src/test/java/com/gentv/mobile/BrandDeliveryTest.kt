package com.gentv.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrandDeliveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun packagedDesktopContainsOnlyTheVerifiedGenTVBuild() {
        val assets = context.assets
        assertEquals(setOf("gentv-home.apk", "build-manifest.json"), assets.list("launcher")!!.toSet())
        val manifest = JSONObject(assets.open("launcher/build-manifest.json").bufferedReader().use { it.readText() })
        val bytes = assets.open("launcher/gentv-home.apk").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(digest, manifest.getJSONObject("sha256").getString("gentv-home.apk"))
        assertTrue(manifest.getBoolean("sourceBuilt"))
    }

    @Test fun productArtworkDoesNotIncludeTheInheritedMascot() {
        for (state in listOf("stand", "search", "happy", "cheer", "worried")) {
            assertEquals(0, context.resources.getIdentifier("mascot_$state", "drawable", context.packageName))
        }
        assertTrue(R.drawable.ic_gentv != 0)
    }

    @Test fun sourceAndEmbeddedDesktopLicencesReachTheRecipient() {
        val files = context.assets.list("licenses")!!.toSet()
        assertTrue(files.containsAll(setOf("LICENSE", "mobile-framework-MIT.txt", "Apache-2.0.txt", "Outfit-OFL.txt", "Nunito-OFL.txt")))
        val mit = context.assets.open("licenses/mobile-framework-MIT.txt").bufferedReader().use { it.readText() }
        assertTrue(mit.contains("Permission is hereby granted"))
    }
}
