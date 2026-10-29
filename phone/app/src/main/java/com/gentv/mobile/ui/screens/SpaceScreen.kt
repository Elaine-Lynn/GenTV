package com.gentv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gentv.mobile.SpaceState
import com.gentv.mobile.ui.AppIcon
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.CardBox
import com.gentv.mobile.ui.InfoChip
import com.gentv.mobile.ui.MoreInfo
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.Outline
import com.gentv.mobile.ui.SafetyBanner
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.mobile.ui.TopBar

private fun gb(mb: Int) = String.format(java.util.Locale.US, "%.1f", mb / 1024.0)

/** Free up space: how full the TV is, which apps hold the most cache, and one button to clear it all. */
@Composable
fun SpaceScreen(s: SpaceState, tvName: String?, labels: Map<String, String>, onClear: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    ScreenFrame(
        top = { TopBar("返回首页", onBack, tvName) },
        bottom = {
            if (!s.loading) BigButton(
                if (s.clearing) (if (s.progressMb > 0) "Freed ${s.progressMb} MB…" else "Clearing…") else "清理应用缓存", onClear, color = GenTVPalette.Green, enabled = !s.clearing && s.storage != null,
                leading = if (s.clearing) ({ Spinner(22.dp, Color(0xFF14213A)) }) else null,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("空间深度清理", Modifier.weight(1f))
            MoreInfo("Caches") {
                Body("Caches are temporary files apps keep to load faster. Clearing them is safe: apps rebuild what they need. Your sign-ins, settings and downloads stay.", color = GenTVPalette.Ink)
            }
        }
        if (s.loading) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(26.dp)
                Spacer(Modifier.width(12.dp))
                Text("正在读取电视存储…", style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Muted)
            }
        }
        s.message?.let { if (it.ok) SafetyBanner(it.message) else Note(it.message, color = GenTVPalette.RedSoft, textColor = GenTVPalette.Red) }
        val st = s.storage
        if (!s.loading && st == null) CardBox {
            Body("暂未取得存储信息。请确认电视连接正常后重试。")
            com.gentv.mobile.ui.TextLink("重新读取存储", onRetry)
        }
        if (!s.loading && st != null) {
            CardBox(color = GenTVPalette.BlueSoft) {
                val free = st.freeMb
                val total = st.totalMb
                if (free != null && total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp)),
                        color = GenTVPalette.Blue, trackColor = GenTVPalette.Grey,
                    )
                    Text("可用 ${gb(free)} GB / 总容量 ${gb(total)} GB", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink, modifier = Modifier.padding(top = 10.dp))
                } else Text("暂时无法读取存储容量", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
                Row(Modifier.padding(top = 10.dp)) {
                    // Android counts app caches only about once a day, so before a clear this is an estimate.
                    InfoChip(Icons.Rounded.Storage, if (s.estimated) "缓存约 ${st.cacheMb} MB" else "缓存已清理", color = GenTVPalette.Grey, textColor = GenTVPalette.Ink)
                }
                if (s.clearing) Text("Android is clearing caches in the background. The free space above updates as it goes.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (!s.loading && s.topCache.isNotEmpty()) Outline {
            Text("应用缓存排行", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
            Text("As of Android's last count, which it refreshes about once a day.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((pkg, mb) in s.topCache) {
                    val name = labels[pkg] ?: com.gentv.core.lists.AppLabels.label(pkg)
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, name, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(name, localize = false, style = MaterialTheme.typography.bodyLarge, color = GenTVPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("$mb MB", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}
