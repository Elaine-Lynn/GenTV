package com.gentv.mobile.data

import android.content.pm.PackageInfo
import android.os.Build

/** Additional checks before the platform installer performs its own signature verification. */
object UpdatePackage {
    @Suppress("DEPRECATION")
    fun verify(archive: PackageInfo, current: PackageInfo, info: UpdateInfo, sdk: Int, packageName: String) {
        require(archive.packageName == packageName)
        val code = if (Build.VERSION.SDK_INT >= 28 && sdk >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val installed = if (Build.VERSION.SDK_INT >= 28 && sdk >= 28) current.longVersionCode else current.versionCode.toLong()
        require(code == info.versionCode && code > installed && archive.versionName == info.version)
        require(info.minSdk <= sdk && archive.applicationInfo!!.minSdkVersion <= sdk)
        fun signers(p: PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28 && sdk >= 28) p.signingInfo?.apkContentsSigners else p.signatures)
            ?.map { it.toCharsString() }?.toSet().orEmpty()
        val trusted = signers(current)
        require(trusted.isNotEmpty() && signers(archive) == trusted) { "APK signing certificate mismatch" }
    }
}
