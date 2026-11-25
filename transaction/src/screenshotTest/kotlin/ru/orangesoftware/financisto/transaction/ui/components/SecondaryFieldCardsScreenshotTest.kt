// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

private val SampleProjects = listOf(
    ProjectOption(id = 1L, title = "House Renovation"),
    ProjectOption(id = 2L, title = "Vacation 2025"),
)

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ProjectFieldCardSelectedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            ProjectFieldCard(
                selectedProjectId = 1L,
                pendingProjectName = null,
                projects = SampleProjects,
                onOpenPicker = {},
                onClear = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ProjectFieldCardPendingPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            ProjectFieldCard(
                selectedProjectId = 0L,
                pendingProjectName = "New Kitchen",
                projects = SampleProjects,
                onOpenPicker = {},
                onClear = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun CreditCardPaymentCardCheckedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            CreditCardPaymentCard(
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ReceiptAttachmentCardAttachedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            ReceiptAttachmentCard(
                attachedFileName = "receipt_01.jpg",
                resolvedFilePath = null,
                onCameraClick = {},
                onBrowseClick = {},
                onRemoveClick = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun TransactionBottomBarNewBlankPreviewTest() {
    ScreenshotSurface {
        TransactionBottomBar(
            isBlankNew = true,
            mode = TransactionMode.EXPENSE,
            isSaving = false,
            onSecondaryAction = {},
            onSaveClick = {},
        )
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun TransactionBottomBarEditingTransferPreviewTest() {
    ScreenshotSurface {
        TransactionBottomBar(
            isBlankNew = false,
            mode = TransactionMode.TRANSFER,
            isSaving = false,
            onSecondaryAction = {},
            onSaveClick = {},
        )
    }
}
