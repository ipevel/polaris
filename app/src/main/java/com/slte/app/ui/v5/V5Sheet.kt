// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slte.app.ui.component.AppLocaleContent
import com.slte.app.ui.component.LocalAppLocale
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.V5SheetShape
import com.slte.app.ui.theme.V5SheetTitleStyle
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.utils.Dimens

/**
 * 底部面板（v6 iOS 语言）：小圆角 + 顶部抓手 + 17sp 居中粗标题。
 *
 * 行为沿用 Material3 `ModalBottomSheet`（滚动、IME 避让、拖拽关闭、无障碍语义都不重写），
 * 只把视觉收敛到 v6：[V5SheetShape]、[V5SheetTitleStyle]、V5 配色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V5Sheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    dismissible: Boolean = true,
    compact: Boolean = false,
    shape: Shape = V5SheetShape,
    titleStyle: TextStyle = V5SheetTitleStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = V5ThemeColors.current
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                dismissible || value != SheetValue.Hidden
            },
        )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 面板不可关闭时，必须在这里把返回键关掉，而不是只忽略 onDismissRequest。
        //
        // Android 13+ 上 `ModalBottomSheet` 会在自己的窗口里注册一个 predictive back 的
        // `OnBackInvokedCallback`（见 material3 `ModalBottomSheetDialogLayout.maybeRegisterBackCallback`），
        // 它走的是平台级返回分发，**绕过 Compose 的 `BackHandler` / `OnBackPressedDispatcher`**：
        // 回调里先用手势进度把面板动画推出屏幕，再调 `onDismissRequest`。所以忽略回调救不回来
        // ——面板已经滑走了，只剩一个"看不见但仍在组合树里"的空壳。`confirmValueChange` 同样拦不住。
        // 雷电模拟器（Android 14）实测：下载中点返回键 → 弹窗消失、下载照跑，用户既看不到进度
        // 也找不到取消。`shouldDismissOnBackPress = false` 会让它干脆不注册那个回调。
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        modifier = modifier,
        shape = shape,
        containerColor = c.surface,
        dragHandle = {
            Box(
                modifier =
                Modifier
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.surface3),
            )
        },
    ) {
        AppLocaleContent(locale = LocalAppLocale.current) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = Dimens.sheetPaddingH,
                        vertical = Dimens.sheetPaddingV,
                    ),
            ) {
                header?.let { headerContent ->

                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { headerContent() }
                }
                if (title != null) {
                    if (header != null) Spacer(modifier = Modifier.height(Dimens.gap.md))
                    Text(
                        text = title,
                        style = titleStyle,
                        color = c.text,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                    Text(
                        text = subtitle,
                        style = SlteType.bodySmall,
                        color = c.text3,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(if (compact) Dimens.gap.sm else Dimens.gap.xl))
                content()
                Spacer(modifier = Modifier.height(Dimens.gap.lg))
            }
        }
    }

    // 第二道保险：API 33 以下没有 predictive back，`ModalBottomSheet` 不注册上面那个回调，
    // 返回键会顺窗口链冒泡到宿主 Activity 的 `OnBackPressedDispatcher`，在那里拦掉。
    // 声明在 `ModalBottomSheet` 之后，因为 Compose 的 `BackHandler` 是后注册者胜。
    if (!dismissible) {
        BackHandler { }
    }
}
