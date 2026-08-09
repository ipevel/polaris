package com.slte.app.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/** HTML 标签检测：含标签即按 HTML 渲染（V2Board/Xboard 公告等富文本） */
private val HTML_TAG_REGEX = Regex("</?[a-zA-Z][^>]*>")

/** 富文本组件：含 HTML 标签走 HtmlCompat，否则 Markdown；标题/正文用小号字（库默认 display 过大）。 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier
) {
    if (HTML_TAG_REGEX.containsMatchIn(text)) {
        HtmlText(html = text, modifier = modifier)
    } else {
        val typography = markdownTypography(
            h1 = MaterialTheme.typography.titleLarge,
            h2 = MaterialTheme.typography.titleMedium,
            h3 = MaterialTheme.typography.titleMedium,
            h4 = MaterialTheme.typography.titleSmall,
            h5 = MaterialTheme.typography.titleSmall,
            h6 = MaterialTheme.typography.titleSmall,
            text = MaterialTheme.typography.bodyMedium,
            paragraph = MaterialTheme.typography.bodyMedium,
            ordered = MaterialTheme.typography.bodyMedium,
            bullet = MaterialTheme.typography.bodyMedium,
            list = MaterialTheme.typography.bodyMedium,
            quote = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            link = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
        )
        Markdown(
            content = text,
            modifier = modifier,
            typography = typography
        )
    }
}
