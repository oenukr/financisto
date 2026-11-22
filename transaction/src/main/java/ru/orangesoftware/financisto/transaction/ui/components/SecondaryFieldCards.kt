// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme
import java.io.File

@Composable
fun ProjectFieldCard(
    selectedProjectId: Long,
    pendingProjectName: String?,
    projects: List<ProjectOption>,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
) {
    val activeTitle = pendingProjectName
        ?: projects.firstOrNull { it.id == selectedProjectId && it.id > 0L }?.title

    FormFieldCardSurface(isActive = false, modifier = Modifier.clickable(onClick = onOpenPicker)) {
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
                    Text("Project", fontSize = 11.sp, color = StitchColors.TextTertiary)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (activeTitle != null) {
                        Surface(
                            shape = CircleShape,
                            color = StitchColors.PrimaryTonal,
                            border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = activeTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StitchColors.OnPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear project",
                                    tint = StitchColors.TextSecondary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable(onClick = onClear),
                                )
                            }
                        }
                    } else {
                        Text("No project", fontSize = 13.sp, color = StitchColors.TextSecondary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistNewPill(label = "Select", icon = Icons.Rounded.CreateNewFolder, onClick = onOpenPicker)
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = StitchColors.TextTertiary)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ReceiptAttachmentCard(
    attachedFileName: String?,
    resolvedFilePath: String?,
    onCameraClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    FormFieldCardSurface(isActive = false) {
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
                    Text("Receipt or Attachment", fontSize = 11.sp, color = StitchColors.TextTertiary)
                    Text(
                        text = attachedFileName ?: "Take a picture",
                        fontSize = 13.sp,
                        fontWeight = if (attachedFileName != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = StitchColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCameraClick,
                    shape = CircleShape,
                    border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.TextTertiary),
                ) {
                    Text("Camera", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchColors.ChipText)
                }
                OutlinedButton(
                    onClick = onBrowseClick,
                    shape = CircleShape,
                    border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.TextTertiary),
                ) {
                    Text("Browse", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchColors.ChipText)
                }
                if (attachedFileName != null) {
                    ClearMiniIconButton(onClick = onRemoveClick, contentDescription = "Remove attachment")
                }
            }
        }
        if (resolvedFilePath != null) {
            Spacer(modifier = Modifier.height(10.dp))
            GlideImage(
                model = File(resolvedFilePath),
                contentDescription = "Receipt preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(StitchDimens.RadiusTile))
                    .blur {
                        blurRadiusSpec = BlurRadiusSpec.verticalGradient(
                            stops = listOf(
                                BlurStop(fraction = 0f, radius = 0.dp),
                                BlurStop(fraction = 0.78f, radius = 0.dp),
                                BlurStop(fraction = 1f, radius = 8.dp),
                            )
                        )
                    },
            )
        }
    }
}

@Composable
fun CreditCardPaymentCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FormFieldCardSurface(
        isActive = false,
        modifier = Modifier.clickable { onCheckedChange(!checked) },
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
                        text = "Credit Card Payment",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchColors.TextPrimary,
                    )
                    Text(
                        text = "Not include into bill total",
                        fontSize = 12.sp,
                        color = StitchColors.TextTertiary,
                    )
                }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = StitchColors.Primary),
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
) {
    Surface(
        color = StitchColors.ScreenBg.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
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
                    containerColor = StitchColors.PrimaryTonal,
                    contentColor = StitchColors.OnPrimaryContainer,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text(
                    text = if (isBlankNew) "Save & New" else "Cancel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Button(
                onClick = onSaveClick,
                enabled = !isSaving,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StitchColors.Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (mode == TransactionMode.TRANSFER) " Save Transfer" else " Save",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private val sampleProjects = listOf(
    ProjectOption(id = 1L, title = "House Renovation"),
    ProjectOption(id = 2L, title = "Vacation 2025"),
)

@Preview(name = "Project Field Card - Selected", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun ProjectFieldCardSelectedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ProjectFieldCard(
                selectedProjectId = 1L,
                pendingProjectName = null,
                projects = sampleProjects,
                onOpenPicker = {},
                onClear = {},
            )
        }
    }
}

@Preview(name = "Project Field Card - No Selection", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun ProjectFieldCardNoSelectionPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ProjectFieldCard(
                selectedProjectId = 0L,
                pendingProjectName = null,
                projects = sampleProjects,
                onOpenPicker = {},
                onClear = {},
            )
        }
    }
}

