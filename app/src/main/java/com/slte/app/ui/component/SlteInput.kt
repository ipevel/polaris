// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.ui.theme.V5ThemeColors
import com.slte.app.utils.Dimens

enum class SlteInputSize { Hero, Compact }

@Composable
fun SlteInput(
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
    bordered: Boolean = true,
    size: SlteInputSize = SlteInputSize.Hero,
) {
    val compact = size == SlteInputSize.Compact
    val fieldHeight = if (compact) Dimens.size.button else Dimens.size.row
    val fieldText = if (compact) SlteType.body else SlteType.field
    val fieldIcon = if (compact) Dimens.icon.md else Dimens.icon.lg
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val c = V5ThemeColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SlteShapes.medium,
        color = c.surface,
        border =
        when {
            focused -> BorderStroke(Dimens.strokeMedium, c.accent)
            bordered -> BorderStroke(Dimens.dividerThickness, c.hairline)
            else -> null
        },
        contentColor = c.text3,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .padding(horizontal = Dimens.gap.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = iconDesc,
                    modifier = Modifier.size(fieldIcon),
                    tint = c.text3,
                )
                Spacer(modifier = Modifier.width(Dimens.gap.sm))
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
                textStyle = fieldText.copy(color = c.text),

                cursorBrush = SolidColor(c.accent),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = fieldText.copy(color = c.text3),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                },
            )
            trailing?.invoke()
        }
    }
}
