package com.gentv.mobile.ui.screens

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gentv.mobile.LookId
import com.gentv.mobile.LookState
import com.gentv.mobile.PickedFile
import com.gentv.mobile.ui.BigButton
import com.gentv.mobile.ui.ChoiceCard
import com.gentv.mobile.ui.InfoChip
import com.gentv.mobile.ui.MoreInfo
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import com.gentv.mobile.ui.Body
import com.gentv.mobile.ui.Note
import com.gentv.mobile.ui.ScreenFrame
import com.gentv.mobile.ui.Spinner
import com.gentv.mobile.ui.GenTVPalette
import com.gentv.mobile.ui.Title
import com.gentv.mobile.ui.TopBar

@Composable
fun LookScreen(
    look: LookState,
    onClose: () -> Unit,
    onApplyLook: (LookId) -> Unit,
    onApplyPhoto: (PickedFile) -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val busy = look.applying != null
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            var name = "photo"
            var size = 0L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val z = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0 && !c.isNull(n)) name = c.getString(n) ?: "photo"
                        if (z >= 0 && !c.isNull(z)) size = c.getLong(z)
                    }
                }
            }
            onApplyPhoto(PickedFile(name, size) { resolver.openInputStream(uri) ?: error("can't open") })
        }
    }
    ScreenFrame(
        top = { TopBar(back = "Home screen", onBack = onClose, tvName = null) },
        bottom = { BigButton("Done", onClose, enabled = !busy) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("Pick a look", Modifier.weight(1f))
            MoreInfo("Looks") {
                Body("Changes the wallpaper, dock and colours of the compatible desktop home screen. Your apps stay the same.", color = GenTVPalette.Ink)
                Body("Your own photo: pick any photo from your phone. GenTV crops it to fit the TV and makes it small before sending.", color = GenTVPalette.Ink)
            }
        }
        look.message?.let { if (it.ok) InfoChip(Icons.Rounded.CheckCircle, it.message) else Note(it.message) }
        val photoOn = look.current == "photo"
        // The looks, then "Your photo" (null) as the last tile.
        for (row in (LookId.entries + null).chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in row) Box(Modifier.weight(1f)) {
                    if (id != null) LookCard(id, selected = look.current == id.id, applying = look.applying == id.id, enabled = !busy) { onApplyLook(id) }
                    else ChoiceCard("Your photo", selected = photoOn, enabled = !busy, onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, preview = {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(GenTVPalette.BlueSoft), contentAlignment = Alignment.Center) {
                            if (look.applying == "photo") Spinner(26.dp)
                            else Icon(Icons.Rounded.AddPhotoAlternate, "Add your photo", Modifier.size(36.dp), tint = GenTVPalette.Blue)
                        }
                    })
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (look.applying == "photo") Text("Making it TV-sized and sending…", style = MaterialTheme.typography.bodyMedium, color = GenTVPalette.Ink)
    }
}

@Composable
private fun LookCard(id: LookId, selected: Boolean, applying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(id) {
        runCatching {
            context.assets.open("themes/${id.id}/screenshot.jpg").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    ChoiceCard(id.title, selected = selected, onClick = onClick, enabled = enabled, preview = {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(GenTVPalette.Ink)) {
            if (bitmap != null) Image(
                bitmap.asImageBitmap(), "${id.title} look",
                Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop,
            )
            if (applying) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Spinner(26.dp, Color(0xFF14213A))
            }
        }
    })
}
