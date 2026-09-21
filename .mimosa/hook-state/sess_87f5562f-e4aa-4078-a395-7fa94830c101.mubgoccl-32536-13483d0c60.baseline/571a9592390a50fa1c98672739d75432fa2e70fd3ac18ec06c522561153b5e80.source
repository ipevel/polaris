package com.slte.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.slte.app.ui.theme.SlteShapes
import com.slte.app.ui.theme.SlteType
import com.slte.app.utils.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlteSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    dismissible: Boolean = true,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
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
        modifier = modifier,
        shape = SlteShapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
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
                        style = SlteType.heading,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(Dimens.gap.sm))
                    Text(
                        text = subtitle,
                        style = SlteType.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
