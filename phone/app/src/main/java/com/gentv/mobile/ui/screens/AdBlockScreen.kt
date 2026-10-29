package com.gentv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gentv.mobile.AdsState
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.CardBox
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.Outline
import com.gentv.mobile.ui.SafetyBanner
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.mobile.ui.TopBar
import com.gentv.core.ops.AdBlockInfo
import com.gentv.core.ops.AdBlockProvider

/** Block ads: Private DNS with AdGuard, and a plain list of what that does and doesn't stop. */
@Composable
fun AdBlockScreen(
    s: AdsState,
    tvName: String?,
    onBlock: (AdBlockProvider, Boolean) -> Unit,
    onUnblock: () -> Unit,
    onCancelReplace: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val on = s.dns?.provider
    ScreenFrame(
        top = { TopBar("返回首页", onBack, tvName) },
        bottom = {
            if (!s.loading && s.supported) {
                if (on != null) BigButton(if (s.busy) "Turning off…" else "关闭广告过滤", onUnblock, color = GenTVPalette.Grey, textColor = GenTVPalette.Ink, enabled = !s.busy && s.dns != null)
                else BigButton(if (s.busy) "Turning on…" else "Block ads and trackers", { onBlock(AdBlockProvider.ADS, false) }, color = GenTVPalette.Green, enabled = !s.busy && s.dns != null)
            }
        },
    ) {
        Title("广告过滤")
        if (s.loading) Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner(26.dp); Spacer(Modifier.width(12.dp))
            Text("正在检查电视…", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Muted)
        }
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message, color = GenTVPalette.RedSoft, textColor = GenTVPalette.Red) }
        if (!s.supported) {
            Note("This TV is too old for ad blocking. It needs Android 9 or newer.")
            return@ScreenFrame
        }
        if (s.loading) return@ScreenFrame
        if (s.dns == null) com.gentv.mobile.ui.TextLink("重新读取设置", onRetry)
        if (on != null) SafetyBanner("Ad blocking is on (${on.blurb}).")
        if (s.dns?.someoneElses == true) Note("This TV already uses its own Private DNS (${s.dns.host}). Turning this on replaces it, and turning it off puts it back.")
        s.confirmReplace?.let { p ->
            Outline {
                Text("Replace the DNS this TV already uses?", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Replace it", { onBlock(p, true) }, Modifier.weight(1f), color = GenTVPalette.Green)
                    BigButton("Keep it", onCancelReplace, Modifier.weight(1f), color = GenTVPalette.Grey, textColor = GenTVPalette.Ink)
                }
            }
        }
        CardBox(color = GenTVPalette.GreenSoft) {
            Text("可过滤的内容", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.GreenInk)
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdBlockInfo.BLOCKS.forEach { Text("✓  $it", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Ink) }
            }
        }
        CardBox(color = GenTVPalette.Amber) {
            Text("无法过滤的内容", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.AmberInk)
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdBlockInfo.DOES_NOT_BLOCK.forEach { Text("✗  $it", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Ink) }
            }
            Text(AdBlockInfo.YOUTUBE_WARNING, style = MaterialTheme.typography.titleMedium, color = GenTVPalette.AmberInk, modifier = Modifier.padding(top = 10.dp))
        }
        Body(AdBlockInfo.SIDE_EFFECT, color = GenTVPalette.Muted)
        if (on == null && !s.busy) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Also block adult sites?", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
            com.gentv.mobile.ui.TextLink("Use the family version", { onBlock(AdBlockProvider.FAMILY, false) })
        }
    }
}
