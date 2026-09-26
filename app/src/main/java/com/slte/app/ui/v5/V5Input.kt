// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.v5

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slte.app.R
import com.slte.app.ui.theme.SlteIcons
import com.slte.app.ui.theme.V5ThemeColors

/**
 * v5 输入框。
 *
 * 与 v4 的 `SlteInput` 是同一条 BasicTextField 骨架（同一套 enabled/readOnly/单行/
 * 键盘类型/视觉变换/IME 动作语义），只把外壳换成 v5 语言：16dp 圆角、`surface2` 底、
 * 聚焦时 1.5dp 蓝色描边、v5 字号刻度。
 *
 * 为什么不做成"给 SlteInput 加 v5 参数"：`SlteInput` 的圆角/边框/取色全部来自
 * `SlteShapes`/`MaterialTheme`，要 v5 化就得把这三样都参数化，等于在一个组件里维护两套皮肤；
 * 而输入框的**行为**部分（上面那些语义）在本组件里是一字不差复刻的，不存在行为分叉。
 */
@Composable
fun V5Input(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDesc: String? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    small: Boolean = false,
) {
    val c = V5ThemeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (small) 14.dp else 16.dp)

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (small) 44.dp else 52.dp)
            .clip(shape)
            .background(if (readOnly) c.surface3 else c.surface2)
            .then(
                if (focused && !readOnly) {
                    Modifier.background(c.accentBg)
                } else {
                    Modifier
                },
            )
            .then(
                if (focused && !readOnly) {
                    Modifier.border(1.5.dp, c.accent, shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 15.dp, vertical = if (small) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = iconDesc,
                modifier = Modifier.size(if (small) 18.dp else 20.dp),
                tint = if (enabled) c.accent else c.text3,
            )
            Box(Modifier.size(10.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            interactionSource = interactionSource,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            visualTransformation = visualTransformation,
            textStyle =
            TextStyle(
                fontSize = if (small) 14.sp else 15.sp,
                color = c.text,
            ),
            cursorBrush = SolidColor(c.accent),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = if (small) 14.sp else 15.sp,
                            color = c.text3,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        trailing?.invoke()
    }
}

/** 输入框下方的一行辅助说明 / 校验提示（v5 危险色）。 */
@Composable
fun V5FieldHint(text: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        color = if (danger) V5ThemeColors.current.danger else V5ThemeColors.current.text3,
    )
}

/** 只读信息栏（v5）：与 [V5Input] 同尺寸，用于"可提现余额"这类不可编辑的值。 */
@Composable
fun V5ReadOnlyField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDesc: String? = null,
) {
    val c = V5ThemeColors.current
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface3)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, iconDesc, modifier = Modifier.size(18.dp), tint = c.accent)
            Box(Modifier.size(10.dp))
        }
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Text(label, fontSize = 11.5.sp, color = c.text3)
    }
}

/**
 * v5 密码输入框：在 [V5Input] 上叠"明文/密文"切换。
 *
 * 与 v4 的 `SltePasswordInput` 行为一致（默认密文、点击切换、切换触发触感、`contentDescription`
 * 走 `login_toggle_password`）；切换按钮从 Material `IconButton`（48dp 触控框 + 涟漪）换成
 * 30dp 圆角图标块，与邀请码复制按钮同规格。
 */
@Composable
fun V5PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDesc: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    small: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val c = V5ThemeColors.current

    V5Input(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        icon = icon,
        iconDesc = iconDesc,
        enabled = enabled,
        imeAction = imeAction,
        small = small,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Box(
                modifier =
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        noRippleClickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            visible = !visible
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (visible) SlteIcons.VisibilityOff else SlteIcons.VisibilityOn,
                    contentDescription = stringResource(R.string.login_toggle_password),
                    modifier = Modifier.size(17.dp),
                    tint = c.text3,
                )
            }
        },
    )
}
