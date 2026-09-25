// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
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
        WhitelistedUriHandler {
            Markdown(
                content = text,
                modifier = modifier,
                typography = typography,
            )
        }
    }
}

/**
 * 只放行 http/https 的链接处理器。
 *
 * markdown 渲染器（0.28.0）**没有**链接点击回调：`MarkdownTextKt` 在内部直接取
 * `LocalUriHandler` 并调用 `openUri`（字节码取证，见 `MarkdownTextKt$...$1$1$1`）。
 * 因此拦截点只能是这个 CompositionLocal —— 覆盖它即可让 markdown 链接与
 * [HtmlText] 走同一套 `isSupportedLinkUrl` 白名单（此前 markdown 分支完全没有白名单，
 * 面板下发的任意 scheme 都会直接交给系统外部打开）。
 * 其余 scheme 静默忽略：这是展示型文案，不是用户可编辑输入。
 */
@Composable
internal fun WhitelistedUriHandler(content: @Composable () -> Unit) {
    val platformHandler = LocalUriHandler.current
    val guarded =
        remember(platformHandler) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    if (isSupportedLinkUrl(uri)) platformHandler.openUri(uri)
                }
            }
        }
    CompositionLocalProvider(LocalUriHandler provides guarded) {
        content()
    }
}
