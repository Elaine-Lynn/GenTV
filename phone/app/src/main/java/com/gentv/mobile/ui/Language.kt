package com.gentv.mobile.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

enum class AppLanguage(val code: String) { ENGLISH("en"), CHINESE("zh");
    companion object { fun from(code: String?) = entries.firstOrNull { it.code == code } ?: ENGLISH }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
val LocalChangeLanguage = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }

/** Legacy screen copy is centralized here; device names, user input and reports bypass this adapter. */
object AppCopy {
    private val entries by lazy { translationEntries() }
    private val english by lazy { entries.asReversed().associate { it.second to it.first } }
    private val chinese by lazy { entries.associate { it.first to it.second } }
    private data class Template(val pattern: Regex, val replacement: String)
    private fun patterns(reverse: Boolean): List<Template> = entries.mapNotNull { (en, zh) ->
        val source = if (reverse) zh else en
        val target = if (reverse) en else zh
        if (!source.contains("{0}")) return@mapNotNull null
        val tokens = Regex("\\{(\\d+)\\}").findAll(source).toList()
        var position = 0
        val pattern = buildString {
            append("^")
            for (token in tokens) {
                append(Regex.escape(source.substring(position, token.range.first)))
                append("(.+?)")
                position = token.range.last + 1
            }
            append(Regex.escape(source.substring(position))); append("$")
        }
        Template(Regex(pattern, RegexOption.DOT_MATCHES_ALL), target)
    }
    private val toEnglish by lazy { patterns(true) }
    private val toChinese by lazy { patterns(false) }
    fun translate(text: String, language: AppLanguage): String {
        val direct = if (language == AppLanguage.ENGLISH) english else chinese
        direct[text]?.let { return it }
        val templates = if (language == AppLanguage.ENGLISH) toEnglish else toChinese
        for (template in templates) {
            val match = template.pattern.matchEntire(text) ?: continue
            return Regex("\\{(\\d+)\\}").replace(template.replacement) { token ->
                match.groupValues.getOrNull(token.groupValues[1].toInt() + 1) ?: token.value
            }
        }
        return text
    }
}

@Composable
fun AppText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current, localize: Boolean = true,
) {
    androidx.compose.material3.Text(
        text = if (localize) AppCopy.translate(text, LocalAppLanguage.current) else text,
        modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}
