package com.slte.app.ui.component

import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.TextSizes

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary

    val annotatedString =
        remember(html, onSurface, linkColor) {
            spannedToAnnotatedString(html, onSurface, linkColor)
        }

    Text(
        text = annotatedString,
        style = SlteType.body,
        color = onSurface,
        modifier = modifier,
    )
}

internal fun isSupportedLinkUrl(url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    return lower.startsWith("https://") || lower.startsWith("http://")
}

private fun spannedToAnnotatedString(
    html: String,
    defaultColor: Color,
    linkColor: Color,
): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val source = spanned.toString()

    data class SpanInfo(
        val start: Int,
        val end: Int,
        val style: SpanStyle,
    )

    data class LinkInfo(
        val start: Int,
        val end: Int,
        val url: String,
    )

    val spans = mutableListOf<SpanInfo>()
    val links = mutableListOf<LinkInfo>()
    val allSpans = spanned.getSpans(0, source.length, Any::class.java)
    for (span in allSpans) {
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        if (start < 0 || end < 0 || start >= end || start > source.length || end > source.length) continue

        when (span) {
            is URLSpan -> {
                val url = span.url?.trim().orEmpty()
                if (isSupportedLinkUrl(url)) links += LinkInfo(start, end, url)
            }
            is StyleSpan -> {
                val fontWeight = if (span.style == Typeface.BOLD) FontWeight.Bold else FontWeight.Normal
                val fontStyle = if (span.style == Typeface.ITALIC) FontStyle.Italic else FontStyle.Normal
                spans += SpanInfo(start, end, SpanStyle(fontWeight = fontWeight, fontStyle = fontStyle))
            }
            is ForegroundColorSpan -> {
                spans += SpanInfo(start, end, SpanStyle(color = Color(span.foregroundColor)))
            }
            is UnderlineSpan -> {
                spans += SpanInfo(start, end, SpanStyle(textDecoration = TextDecoration.Underline))
            }
            is RelativeSizeSpan -> {
                spans += SpanInfo(start, end, SpanStyle(fontSize = (TextSizes.htmlBaseFontSize.value * span.sizeChange).sp))
            }
        }
    }

    return buildAnnotatedString {
        append(source)
        for (info in spans) {
            addStyle(info.style, info.start, info.end)
        }

        for (link in links) {
            addLink(
                LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
                link.start,
                link.end,
            )
        }
    }
}
