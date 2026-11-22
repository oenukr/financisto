// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerRounding
import androidx.compose.foundation.shape.MorphPolygonShape
import androidx.compose.foundation.shape.PolygonShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

@Composable
fun FormFieldCardSurface(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val borderColor = if (isActive) StitchColors.ActiveBorder else StitchColors.CardBorder
    val borderWidth = if (isActive) StitchDimens.ActiveBorderWidth else StitchDimens.DefaultBorderWidth
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StitchDimens.RadiusFormCard),
        color = StitchColors.SurfaceWhite,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (isActive) 3.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(StitchDimens.CardPadding)) {
            content()
        }
    }
}

@Composable
fun rememberSyncedTextFieldState(
    value: String,
    onValueChanged: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(initialText = value)
    val latestOnValueChanged by rememberUpdatedState(onValueChanged)
    val latestValue by rememberUpdatedState(value)

    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { current ->
                if (current != latestValue) {
                    latestOnValueChanged(current)
                }
            }
    }
    return state
}

@Composable
fun LeadingIconTile(
    icon: ImageVector,
    isActive: Boolean = false,
    tintColor: Color = if (isActive) StitchColors.ActiveBorder else StitchColors.TextSecondary,
    bgColor: Color = if (isActive) StitchColors.PrimaryTonal else StitchColors.IconTileBg,
) {
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        label = "iconTileMorph",
    )
    val tileShape = remember {
        MorphPolygonShape(
            start = PolygonShape.rectangle(CornerRounding.dp(StitchDimens.RadiusTile, smoothing = 0.4f)),
            end = PolygonShape.circle(),
            progress = { activeProgress },
        )
    }
    Surface(
        shape = tileShape,
        color = bgColor,
        modifier = Modifier.size(StitchDimens.TileSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
fun AssistNewPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = StitchColors.TopChipBg,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StitchColors.ChipText,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = StitchColors.ChipText,
            )
        }
    }
}

@Composable
fun SuggestionOptionRow(
    title: String,
    tagLabel: String,
    isHighlighted: Boolean,
    icon: ImageVector = Icons.Rounded.History,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = if (isHighlighted) StitchColors.TopChipBg else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StitchDimens.RadiusTile))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlighted) StitchColors.Primary else StitchColors.TextTertiary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = StitchColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tagLabel.isNotEmpty()) {
                Text(
                    text = tagLabel,
                    fontSize = 11.sp,
                    color = StitchColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
fun CreateInlineSuggestionButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = StitchColors.PrimaryTonal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StitchDimens.RadiusTile))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AddCircle,
                contentDescription = null,
                tint = StitchColors.ActiveBorder,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = StitchColors.ActiveBorder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ClearMiniIconButton(onClick: () -> Unit, contentDescription: String = "Clear") {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = contentDescription,
            tint = StitchColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(name = "Form Field Card Surface - Inactive", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun FormFieldCardSurfaceInactivePreview() {
    TransactionStitchTheme {
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
                            fontSize = 12.sp,
                            color = StitchColors.TextTertiary,
                        )
                        Text(
                            text = "Supermarket",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Form Field Card Surface - Active", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun FormFieldCardSurfaceActivePreview() {
    TransactionStitchTheme {
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
                            fontSize = 12.sp,
                            color = StitchColors.TextTertiary,
                        )
                        Text(
                            text = "Supermarket",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Leading Icon Tile - Inactive", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun LeadingIconTileInactivePreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            LeadingIconTile(
                icon = Icons.Rounded.History,
                isActive = false,
            )
        }
    }
}

@Preview(name = "Leading Icon Tile - Active", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun LeadingIconTileActivePreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            LeadingIconTile(
                icon = Icons.Rounded.History,
                isActive = true,
            )
        }
    }
}

@Preview(name = "Assist New Pill", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun AssistNewPillPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AssistNewPill(
                label = "New Payee",
                icon = Icons.Rounded.AddCircle,
                onClick = {},
            )
        }
    }
}

@Preview(name = "Suggestion Option Row - Normal", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SuggestionOptionRowNormalPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SuggestionOptionRow(
                title = "Supermarket",
                tagLabel = "Payee",
                isHighlighted = false,
                onClick = {},
            )
        }
    }
}

@Preview(name = "Suggestion Option Row - Highlighted", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SuggestionOptionRowHighlightedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SuggestionOptionRow(
                title = "Supermarket",
                tagLabel = "Payee",
                isHighlighted = true,
                onClick = {},
            )
        }
    }
}

@Preview(name = "Clear Mini Icon Button", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun ClearMiniIconButtonPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ClearMiniIconButton(onClick = {})
        }
    }
}

