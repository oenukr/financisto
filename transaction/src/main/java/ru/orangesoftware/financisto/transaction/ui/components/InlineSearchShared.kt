// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerRounding
import androidx.compose.foundation.shape.MorphPolygonShape
import androidx.compose.foundation.shape.PolygonShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R

private const val MaxPendingEmitted = 64
private const val TileCornerSmoothing = 0.4f

val DecimalInputTransformation: InputTransformation = object : InputTransformation {
    override val keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    override fun TextFieldBuffer.transformInput() {
        if (!isValidDecimalInput(asCharSequence())) {
            revertAllChanges()
        }
    }
}

internal fun isValidDecimalInput(text: CharSequence): Boolean {
    var separatorSeen = false
    for (index in 0 until text.length) {
        val ch = text[index]
        val isSeparator = ch == '.' || ch == ','
        when {
            ch in '0'..'9' -> Unit
            isSeparator && !separatorSeen -> separatorSeen = true
            else -> return false
        }
    }
    return true
}

@Composable
fun FormFieldCardSurface(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val borderColor = if (isActive) semantic.activeBorder else semantic.cardBorder
    val borderWidth = if (isActive) dimens.ActiveBorderWidth else dimens.DefaultBorderWidth
    val shape = RoundedCornerShape(dimens.RadiusFormCard)
    val elevation = if (isActive) 3.dp else 1.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(borderWidth, borderColor),
            shadowElevation = elevation,
        ) {
            Column(
                modifier = contentModifier
                    .fillMaxWidth()
                    .padding(dimens.CardPadding),
            ) {
                content()
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(borderWidth, borderColor),
            shadowElevation = elevation,
        ) {
            Column(
                modifier = contentModifier
                    .fillMaxWidth()
                    .padding(dimens.CardPadding),
            ) {
                content()
            }
        }
    }
}

private class SyncedTextFieldTracker(initialText: String) {
    val pendingEmitted = ArrayDeque<String>()
    var lastSyncedText: String = initialText
    var reconcileToken by mutableIntStateOf(0)

    fun consumeEchoOrExternal(value: String, currentText: String): Boolean {
        val echoIndex = pendingEmitted.indexOf(value)
        return if (echoIndex >= 0) {
            repeat(echoIndex + 1) { pendingEmitted.removeFirst() }
            if (pendingEmitted.lastOrNull() == value && currentText == value) {
                reconcileToken++
            }
            false
        } else {
            pendingEmitted.clear()
            lastSyncedText = value
            currentText != value
        }
    }
}

internal fun <T> filterAndRankSuggestions(
    items: List<T>,
    query: String,
    limit: Int,
    titleOf: (T) -> String,
): List<T> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return items.take(limit)
    val exact = mutableListOf<T>()
    val prefix = mutableListOf<T>()
    val substring = mutableListOf<T>()
    for (item in items) {
        val title = titleOf(item)
        when {
            title.equals(trimmed, ignoreCase = true) -> exact.add(item)
            title.startsWith(trimmed, ignoreCase = true) -> prefix.add(item)
            title.contains(trimmed, ignoreCase = true) -> substring.add(item)
        }
    }
    return sequenceOf(exact, prefix, substring).flatten().take(limit).toList()
}

@Composable
fun rememberSyncedTextFieldState(
    value: String,
    onValueChanged: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(initialText = value)
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChanged by rememberUpdatedState(onValueChanged)
    val tracker = remember(state) { SyncedTextFieldTracker(initialText = state.text.toString()) }

    LaunchedEffect(value) {
        if (tracker.consumeEchoOrExternal(value, state.text.toString())) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(tracker.reconcileToken) {
        if (tracker.reconcileToken > 0) {
            withFrameNanos { }
            if (latestValue == state.text.toString() && tracker.pendingEmitted.lastOrNull() == latestValue) {
                tracker.pendingEmitted.clear()
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { current ->
                if (current != tracker.lastSyncedText) {
                    tracker.lastSyncedText = current
                    if (tracker.pendingEmitted.size >= MaxPendingEmitted) {
                        tracker.pendingEmitted.removeFirst()
                    }
                    tracker.pendingEmitted.addLast(current)
                    latestOnValueChanged(current)
                    if (current == latestValue) {
                        tracker.reconcileToken++
                    }
                }
            }
    }
    return state
}

@Composable
fun LeadingIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    tintColor: Color = if (isActive) {
        FinancistoTheme.semanticColors.activeBorder
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
    bgColor: Color = if (isActive) {
        FinancistoTheme.semanticColors.primaryTonal
    } else {
        FinancistoTheme.semanticColors.iconTileBackground
    },
) {
    val dimens = FinancistoTheme.dimens
    val activeProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        label = "iconTileMorph",
    )
    val tileShape = remember {
        MorphPolygonShape(
            start = PolygonShape.rectangle(
                CornerRounding.dp(dimens.RadiusTile, smoothing = TileCornerSmoothing),
            ),
            end = PolygonShape.circle(),
            progress = { activeProgress },
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(dimens.TileSize)
            .background(color = bgColor, shape = tileShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun AssistNewPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = semantic.topChipBackground,
            labelColor = semantic.chipText,
            leadingIconContentColor = semantic.chipText,
        ),
        border = BorderStroke(dimens.DefaultBorderWidth, semantic.heroBorder),
        modifier = modifier,
    )
}

@Composable
fun SuggestionOptionRow(
    title: String,
    tagLabel: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.History,
) {
    val semantic = FinancistoTheme.semanticColors
    val rowShape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile)
    val bgColor = if (isHighlighted) semantic.topChipBackground else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(rowShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                tint = if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tagLabel.isNotEmpty()) {
            Text(
                text = tagLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun CreateInlineSuggestionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = semantic.primaryTonal,
            contentColor = semantic.activeBorder,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Rounded.AddCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ClearMiniIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.tx_cd_clear),
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(name = "Form Field Card Surface - Light", showBackground = true)
@Composable
private fun FormFieldCardSurfaceLightPreview() {
    FinancistoTheme {
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

@Preview(name = "Form Field Card Surface - Dark Active", showBackground = true)
@Composable
private fun FormFieldCardSurfaceDarkActivePreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
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
}

@Preview(name = "Assist New Pill & Suggestion Row", showBackground = true)
@Composable
private fun SharedControlsPreview() {
    FinancistoTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistNewPill(label = "New Payee", icon = Icons.Rounded.AddCircle, onClick = {})
            SuggestionOptionRow(title = "Supermarket", tagLabel = "Payee", isHighlighted = true, onClick = {})
            CreateInlineSuggestionButton(text = "Create new payee", onClick = {})
            ClearMiniIconButton(onClick = {})
        }
    }
}
