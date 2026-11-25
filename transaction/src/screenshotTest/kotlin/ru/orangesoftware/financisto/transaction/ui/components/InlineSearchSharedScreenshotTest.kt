// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun FormFieldCardSurfaceInactivePreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            FormFieldCardSurface(isActive = false) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LeadingIconTile(icon = Icons.Rounded.History, isActive = false)
                    Column {
                        Text(
                            text = "Payee",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            text = "Supermarket",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun FormFieldCardSurfaceActivePreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            FormFieldCardSurface(isActive = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LeadingIconTile(icon = Icons.Rounded.History, isActive = true)
                    Column {
                        Text(
                            text = "Payee",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            text = "Supermarket",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun InlineSearchSharedControlsPreviewTest() {
    ScreenshotSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistNewPill(label = "New Payee", icon = Icons.Rounded.AddCircle, onClick = {})
            SuggestionOptionRow(title = "Supermarket", tagLabel = "Payee", isHighlighted = true, onClick = {})
            SuggestionOptionRow(title = "Corner Coffee Shop", tagLabel = "Payee", isHighlighted = false, onClick = {})
            CreateInlineSuggestionButton(text = "Create new payee", onClick = {})
        }
    }
}
