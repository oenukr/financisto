// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme

@Preview(name = "CompactW-CompactH 400x400", widthDp = 400, heightDp = 400, showBackground = true)
@Preview(name = "CompactW-MediumH 400x500", widthDp = 400, heightDp = 500, showBackground = true)
@Preview(name = "CompactW-ExpandedH 400x1000", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "MediumW-CompactH 610x400", widthDp = 610, heightDp = 400, showBackground = true)
@Preview(name = "MediumW-MediumH 610x500", widthDp = 610, heightDp = 500, showBackground = true)
@Preview(name = "MediumW-ExpandedH 610x1000", widthDp = 610, heightDp = 1000, showBackground = true)
@Preview(name = "ExpandedW-CompactH 900x400", widthDp = 900, heightDp = 400, showBackground = true)
@Preview(name = "ExpandedW-MediumH 900x500", widthDp = 900, heightDp = 500, showBackground = true)
@Preview(name = "ExpandedW-ExpandedH 900x1000", widthDp = 900, heightDp = 1000, showBackground = true)
annotation class ScreenSizeMatrixPreviews

@Preview(
    name = "Mobile Dark 400x500",
    widthDp = 400,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "Mobile FontScale 1_5x 400x500",
    widthDp = 400,
    heightDp = 500,
    fontScale = 1.5f,
    showBackground = true,
)
annotation class ScreenMobileVariationPreviews

@Preview(
    name = "Light",
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "FontScale 1_5x",
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
    fontScale = 1.5f,
    showBackground = true,
)
annotation class ComponentScreenshotPreviews

@Preview(
    name = "Light",
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
annotation class ThemeScreenshotPreviews

@Composable
fun ScreenshotSurface(
    content: @Composable () -> Unit,
) {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
