package com.slte.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.slte.app.ui.theme.SlteColors
import com.slte.app.ui.theme.SlteShapes

/**
 * 项目统一输入框：封装认证页 OutlinedTextField 的公共配置。
 *
 * 默认应用：fillMaxWidth、SlteShapes.medium 圆角、InputFieldColors 配色、
 * 单行模式、placeholder 走 SlteColors。
 */
@Composable
fun SlteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconDesc: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(text = placeholder, color = SlteColors.current.inputFieldPlaceholder)
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = leadingIconDesc,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = SlteShapes.medium,
        colors = InputFieldColors(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        enabled = enabled
    )
}
