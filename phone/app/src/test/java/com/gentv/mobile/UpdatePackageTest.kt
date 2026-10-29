package com.gentv.mobile

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import com.gentv.mobile.data.UpdateInfo
import com.gentv.mobile.data.UpdatePackage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION")
class UpdatePackageTest {
    private fun apk(code: Int, signer: String = "abcd") = PackageInfo().apply {
        packageName = BuildConfig.APPLICATION_ID; versionName = "1.0.0"; versionCode = code
        signatures = arrayOf(Signature(signer))
        applicationInfo = ApplicationInfo().apply { minSdkVersion = 26 }
    }
    private val info = UpdateInfo("1.0.0", "", 12)
    @Test fun samePackageNewerVersionSameSignaturePasses() {
        UpdatePackage.verify(apk(12), apk(11), info, 26, BuildConfig.APPLICATION_ID)
    }
    @Test fun mismatchedPackageVersionSignerOrSdkIsRejected() {
        val bad = listOf(apk(12, "1234"), apk(12).apply { packageName = "another.app" }, apk(13),
            apk(12).apply { applicationInfo!!.minSdkVersion = 27 }, apk(12).apply { signatures = emptyArray() },
            apk(12).apply { versionName = "2.0.0" })
        bad.forEach { archive -> assertThrows(IllegalArgumentException::class.java) { UpdatePackage.verify(archive, apk(11), info, 26, BuildConfig.APPLICATION_ID) } }
        assertThrows(IllegalArgumentException::class.java) { UpdatePackage.verify(apk(12), apk(12), info, 26, BuildConfig.APPLICATION_ID) }
    }
    @Test fun modernSigningInfoIsCheckedInsteadOfLegacySignatures() {
        fun modern(signer: String, code: Int) = apk(code).apply {
            signingInfo = android.content.pm.SigningInfo().also {
                org.robolectric.Shadows.shadowOf(it).setSignatures(arrayOf(Signature(signer)))
            }
        }
        UpdatePackage.verify(modern("abcd", 12), modern("abcd", 11), info, 35, BuildConfig.APPLICATION_ID)
        assertThrows(IllegalArgumentException::class.java) {
            UpdatePackage.verify(modern("1234", 12), modern("abcd", 11), info, 35, BuildConfig.APPLICATION_ID)
        }
    }

}
