// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.slte.app.ui.theme.SlteType

private val HTML_TAG_REGEX = Regex("</?[a-zA-Z][^>]*>")

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (HTML_TAG_REGEX.containsMatchIn(text)) {
        HtmlText(html = text, modifier = modifier)
    } else {
        val typography =
            markdownTypography(
                h1 = SlteType.heading,
                h2 = SlteType.title,
                h3 = SlteType.title,
                h4 = SlteType.body,
                h5 = SlteType.body,
                h6 = SlteType.body,
                text = SlteType.body,
                paragraph = SlteType.body,
                ordered = SlteType.body,
                bullet = SlteType.body,
                list = SlteType.body,
                quote = SlteType.body.copy(fontStyle = FontStyle.Italic),
                link =
                SlteType.body.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            )
        Markdown(
            content = text,
            modifier = modifier,
            typography = typography,
        )
    }
}
