package com.gentv.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** The stars worth asking for: 4 and 5 open the official website, anything lower comes to us. */
const val HAPPY_STARS = 4

/** "Enjoying GenTV?" with five stars. [onRate] gets the number; [onLater] is "Maybe later". */
@Composable
fun RatingPrompt(onRate: (Int) -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        CardBox(color = GenTVPalette.Card) {
            var picked by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMascot(mood = Mood.CHEER, height = 110.dp)
                Text("Enjoying GenTV?", style = MaterialTheme.typography.headlineSmall, color = GenTVPalette.Ink, textAlign = TextAlign.Center)
                Text("Choose a rating. 4–5 stars opens our official website; 1–3 stars opens feedback.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in 1..5) Icon(
                        if (n <= picked) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
                        Modifier.size(48.dp).clip(CircleShape)
                            .clickable(role = Role.Button) { picked = n; onRate(n) }
                            .semantics { contentDescription = "$n ${if (n == 1) "star" else "stars"}" }
                            .padding(4.dp),
                        tint = GenTVPalette.Orange,
                    )
                }
                Spacer(Modifier.height(2.dp))
                TextLink("Maybe later", onLater, color = GenTVPalette.Muted, small = true)
            }
        }
    }
}
