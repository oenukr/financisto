// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.LocationOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

private val SamplePayees = listOf(
    PayeeOption(id = 1L, title = "Supermarket Grocery"),
    PayeeOption(id = 2L, title = "Corner Coffee Shop"),
    PayeeOption(id = 3L, title = "Gas Station & Auto"),
)

private val SampleLocations = listOf(
    LocationOption(id = 1L, title = "Main Office"),
    LocationOption(id = 2L, title = "Downtown Mall"),
)

private val SampleRecentNotes = listOf("Weekly groceries", "Gas station", "Dinner with team")

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun PayeeFieldCardExpandedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            PayeeFieldCard(
                query = "Coffee",
                selectedPayeeId = 0L,
                pendingPayeeName = null,
                payees = SamplePayees,
                isExpanded = true,
                onToggleExpand = {},
                onQueryChanged = {},
                onSelectPayee = {},
                onCreatePendingPayee = {},
                onClear = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun LocationFieldCardCollapsedSelectedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            LocationFieldCard(
                query = "",
                selectedLocationId = 1L,
                locations = SampleLocations,
                isExpanded = false,
                onToggleExpand = {},
                onQueryChanged = {},
                onSelectLocation = {},
                onClear = {},
                onCreateNewLocation = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun NoteFieldCardWithRecentChipsPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            NoteFieldCard(
                note = "Weekly groceries",
                recentNotes = SampleRecentNotes,
                onNoteChanged = {},
                onSelectRecentNote = {},
                onClear = {},
            )
        }
    }
}
