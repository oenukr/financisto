// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import java.io.File

private const val BottomBarBackgroundAlpha = 0.96f

@Composable
fun ProjectFieldCard(
    selectedProjectId: Long,
    pendingProjectName: String?,
    projects: List<ProjectOption>,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val activeTitle = remember(projects, selectedProjectId, pendingProjectName) {
        pendingProjectName ?: projects.firstOrNull { it.id == selectedProjectId && it.id > 0L }?.title
    }

    FormFieldCardSurface(
        isActive = false,
        modifier = modifier,
        onClick = onOpenPicker,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LeadingIconTile(icon = Icons.Rounded.Folder)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tx_label_project),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (activeTitle != null) {
                        ProjectSelectedChip(title = activeTitle, enabled = enabled, onClear = onClear)
                    } else {
                        Text(
                            text = stringResource(R.string.tx_placeholder_no_project),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistNewPill(
                    label = stringResource(R.string.tx_action_select),
                    icon = Icons.Rounded.CreateNewFolder,
                    enabled = enabled,
                    onClick = onOpenPicker,
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ProjectSelectedChip(
    title: String,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    InputChip(
        selected = true,
        onClick = onClear,
        enabled = enabled,
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.tx_cd_clear_project),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = semantic.primaryTonal,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = enabled,
            selected = true,
            selectedBorderColor = semantic.heroBorder,
            selectedBorderWidth = dimens.DefaultBorderWidth,
        ),
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ReceiptAttachmentCard(
    attachedFileName: String?,
    resolvedFilePath: String?,
    onCameraClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val receiptFile = remember(resolvedFilePath) { resolvedFilePath?.let(::File) }

    FormFieldCardSurface(isActive = false, modifier = modifier) {
        ReceiptAttachmentHeaderRow(
            attachedFileName = attachedFileName,
            enabled = enabled,
            onCameraClick = onCameraClick,
            onBrowseClick = onBrowseClick,
            onRemoveClick = onRemoveClick,
        )
        if (receiptFile != null) {
            Spacer(modifier = Modifier.height(10.dp))
            GlideImage(
                model = receiptFile,
                contentDescription = stringResource(R.string.tx_cd_receipt_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(FinancistoTheme.dimens.RadiusTile)),
            )
        }
    }
}

@Composable
private fun ReceiptAttachmentHeaderRow(
    attachedFileName: String?,
    enabled: Boolean,
    onCameraClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconTile(icon = Icons.Rounded.PhotoCamera)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tx_label_receipt_attachment),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = attachedFileName ?: stringResource(R.string.tx_placeholder_take_picture),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (attachedFileName != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ReceiptAttachmentActions(
            hasAttachment = attachedFileName != null,
            enabled = enabled,
            onCameraClick = onCameraClick,
            onBrowseClick = onBrowseClick,
            onRemoveClick = onRemoveClick,
        )
    }
}

@Composable
private fun ReceiptAttachmentActions(
    hasAttachment: Boolean,
    enabled: Boolean,
    onCameraClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val buttonBorder = BorderStroke(FinancistoTheme.dimens.DefaultBorderWidth, MaterialTheme.colorScheme.outline)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onCameraClick,
            enabled = enabled,
            shape = CircleShape,
            border = buttonBorder,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.tx_action_camera),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantic.chipText,
            )
        }
        OutlinedButton(
            onClick = onBrowseClick,
            enabled = enabled,
            shape = CircleShape,
            border = buttonBorder,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.tx_action_browse),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantic.chipText,
            )
        }
        if (hasAttachment) {
            ClearMiniIconButton(
                onClick = onRemoveClick,
                contentDescription = stringResource(R.string.tx_cd_remove_attachment),
                enabled = enabled,
            )
        }
    }
}

@Composable
fun CreditCardPaymentCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cardShape = RoundedCornerShape(FinancistoTheme.dimens.RadiusFormCard)
    FormFieldCardSurface(
        isActive = false,
        modifier = modifier,
        contentModifier = Modifier
            .clip(cardShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LeadingIconTile(icon = Icons.Rounded.CreditCard)
                Column {
                    Text(
                        text = stringResource(R.string.tx_label_ccard_payment),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.tx_subtitle_ccard_payment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun TransactionBottomBar(
    isBlankNew: Boolean,
    mode: TransactionMode,
    isSaving: Boolean,
    onSecondaryAction: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    val secondaryLabelRes = if (isBlankNew) R.string.tx_action_save_and_new else R.string.tx_action_cancel
    val saveLabelRes = when (mode) {
        TransactionMode.TRANSFER -> R.string.tx_action_save_transfer
        else -> R.string.tx_action_save
    }

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = BottomBarBackgroundAlpha),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSecondaryAction,
                enabled = !isSaving,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = semantic.primaryTonal,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(secondaryLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            BottomBarSaveButton(
                label = stringResource(saveLabelRes),
                enabled = !isSaving,
                onClick = onSaveClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarSaveButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.height(48.dp),
    ) {
        Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val SampleProjects = listOf(
    ProjectOption(id = 1L, title = "House Renovation"),
    ProjectOption(id = 2L, title = "Vacation 2025"),
)

@Preview(name = "Project & CCard Cards - Light", showBackground = true)
@Composable
private fun SecondaryCardsLightPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectFieldCard(
                    selectedProjectId = 1L,
                    pendingProjectName = null,
                    projects = SampleProjects,
                    onOpenPicker = {},
                    onClear = {},
                )
                CreditCardPaymentCard(checked = true, onCheckedChange = {})
            }
        }
    }
}

@Preview(name = "Bottom Bar & Project - Dark", showBackground = true)
@Composable
private fun SecondaryCardsDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.padding(16.dp)) {
                    ProjectFieldCard(
                        selectedProjectId = 0L,
                        pendingProjectName = null,
                        projects = SampleProjects,
                        onOpenPicker = {},
                        onClear = {},
                    )
                }
                TransactionBottomBar(
                    isBlankNew = true,
                    mode = TransactionMode.EXPENSE,
                    isSaving = false,
                    onSecondaryAction = {},
                    onSaveClick = {},
                )
            }
        }
    }
}
