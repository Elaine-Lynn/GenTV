package com.gentv.mobile.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gentv.mobile.data.FeedbackDraft
import com.gentv.mobile.data.FeedbackKind
import com.gentv.mobile.data.GenTVFeedback
import kotlinx.coroutines.launch

/** Where the feedback sheet was opened from, and how it starts. */
data class FeedbackRequest(val kind: FeedbackKind, val page: String, val prefill: String = "")

/** Things any screen can ask for. The defaults do nothing, so previews and screenshot tests need no setup. */
class AppActions(
    val settings: () -> Unit = {},
    val downloadUpdate: (com.gentv.mobile.data.UpdateInfo) -> Unit = {},
    val feedback: (FeedbackRequest) -> Unit = {},
    val shareCard: () -> Unit = {},
    val openUrl: (String) -> Unit = {},
    /** Open the rating card now (the person asked; not counted as an automatic ask). */
    val rate: () -> Unit = {},
)

val LocalAppActions = staticCompositionLocalOf { AppActions() }

/** "Report a problem", next to every "Share logs". */
@Composable
fun ReportProblemLink(page: String) {
    val actions = LocalAppActions.current
    TextLink("Report a problem", { actions.feedback(FeedbackRequest(FeedbackKind.BUG, page)) })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackSheet(
    request: FeedbackRequest,
    onSend: suspend (FeedbackDraft, includeLog: Boolean) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(request.kind) }
    var message by rememberSaveable { mutableStateOf(request.prefill) }
    var email by rememberSaveable { mutableStateOf("") }
    var includeLog by rememberSaveable { mutableStateOf(request.kind == FeedbackKind.BUG) }
    var screenshot by remember { mutableStateOf<ByteArray?>(null) }
    // idle, sending, sent, failed
    var state by rememberSaveable { mutableStateOf("idle") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            screenshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let(GenTVFeedback::shrinkScreenshot)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = GenTVPalette.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state == "sent") {
                HeroMascot(mood = Mood.HAPPY, height = 120.dp)
                Title("Thank you!", align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Body(
                    if (kind == FeedbackKind.BUG) "Your message and log are with us. Each real TV log helps make GenTV safer for everyone."
                    else "Your message is with us. It really helps.",
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
                BigButton("Done", onDismiss)
                return@Column
            }
            Title("Tell us how it went")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackKind.entries.forEach { k -> Chip(k.label, k == kind) { kind = k; if (k == FeedbackKind.BUG) includeLog = true } }
            }
            OutlinedTextField(
                message, { message = it }, Modifier.fillMaxWidth().heightIn(min = 130.dp),
                placeholder = {
                    Text(
                        when (kind) {
                            FeedbackKind.BUG -> "What happened? Which step were you on?"
                            FeedbackKind.PRAISE -> "What did you like?"
                            FeedbackKind.FEATURE -> "What would make GenTV better?"
                            FeedbackKind.QUESTION -> "What would you like to know?"
                        },
                    )
                },
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Your email, if you'd like a reply (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Checkbox) { includeLog = !includeLog }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TickBox(includeLog)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Include the GenTV log", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.Ink)
                    Text("What GenTV did on your TV. Serial numbers, Wi-Fi names and IP addresses are removed.", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Muted)
                }
            }
            if (screenshot == null) {
                TextLink("+ Attach a screenshot (optional)", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Check(GenTVPalette.Green, 22.dp); Spacer(Modifier.width(8.dp))
                    Text("Screenshot attached", style = MaterialTheme.typography.titleMedium, color = GenTVPalette.GreenInk, modifier = Modifier.weight(1f))
                    TextLink("Remove", { screenshot = null }, color = GenTVPalette.Muted)
                }
            }
            if (state == "failed") Note(if (com.gentv.mobile.data.GenTVFeedback.ENDPOINT.isBlank()) "GenTV feedback service is not configured yet. Your message is still here; use Share logs to export a report." else "That didn't send. Check your internet and try again; your message is still here.")
            BigButton(
                if (state == "sending") "Sending…" else "Send",
                {
                    state = "sending"
                    scope.launch {
                        val ok = onSend(FeedbackDraft(kind, message, email, request.page, screenshot = screenshot), includeLog)
                        state = if (ok) "sent" else "failed"
                    }
                },
                enabled = message.isNotBlank() && state != "sending",
            )
            Text(
                "Your phone model, Android version and GenTV version are sent with your message. Screenshots are deleted after 30 days.",
                style = MaterialTheme.typography.bodySmall, color = GenTVPalette.Muted,
            )
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        color = if (selected) Color.White else GenTVPalette.Ink,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) GenTVPalette.Blue else GenTVPalette.BlueSoft)
            .border(2.dp, if (selected) GenTVPalette.Ink else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The share card preview, with the three things a happy user can do next. */
@Composable
fun ShareCardDialog(card: Bitmap, onShare: () -> Unit, onStar: () -> Unit, onTellUs: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(GenTVPalette.Card).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                card.asImageBitmap(), "Your GenTV card: how many apps you turned off.",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(2.dp, GenTVPalette.Ink, RoundedCornerShape(18.dp)),
                contentScale = ContentScale.FillWidth,
            )
            Body("Show your friends how you tidied your TV. GenTV is free, so sharing is how people find it.", align = androidx.compose.ui.text.style.TextAlign.Center)
            BigButton("Share my card", onShare, color = GenTVPalette.Blue)
            BigButton("Visit official website", onStar, color = Color.White, textColor = GenTVPalette.Ink, leading = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Star, null, tint = GenTVPalette.Ink) })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextLink("Tell us what you liked", onTellUs)
                TextLink("Not now", onDismiss, color = GenTVPalette.Muted)
            }
        }
    }
}
