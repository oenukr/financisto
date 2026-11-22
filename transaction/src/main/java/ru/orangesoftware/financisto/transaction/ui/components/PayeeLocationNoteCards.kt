// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlexAlignItems
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.LocationOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds

private const val MaxInlineSuggestions = 5

@Composable
fun PayeeFieldCard(
    query: String,
    selectedPayeeId: Long,
    pendingPayeeName: String?,
    payees: List<PayeeOption>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSelectPayee: (Long) -> Unit,
    onCreatePendingPayee: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePayeeName = remember(payees, selectedPayeeId, pendingPayeeName) {
        payees.firstOrNull { it.id == selectedPayeeId }?.title ?: pendingPayeeName
    }
    val (filtered, hasExactMatch) = remember(query, payees) {
        val trimmed = query.trim()
        val ranked = filterAndRankSuggestions(payees, trimmed, MaxInlineSuggestions) { it.title }
        val exact = trimmed.isNotEmpty() && payees.any { it.title.equals(trimmed, ignoreCase = true) }
        ranked to exact
    }

    FormFieldCardSurface(isActive = isExpanded, modifier = modifier) {
        PayeeCardHeaderRow(
            query = query,
            activePayeeName = activePayeeName,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            onQueryChanged = onQueryChanged,
            onClear = onClear,
        )
        CapturedAnimatedVisibility(visible = isExpanded) {
            PayeeSuggestionsSection(
                query = query,
                suggestions = filtered,
                selectedPayeeId = selectedPayeeId,
                showCreateButton = query.trim().isNotEmpty() && !hasExactMatch,
                onSelectPayee = onSelectPayee,
                onCreatePendingPayee = onCreatePendingPayee,
            )
        }
    }
}

@Composable
private fun PayeeCardHeaderRow(
    query: String,
    activePayeeName: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val displayValue = activePayeeName ?: query.ifBlank { stringResource(R.string.tx_placeholder_no_payee) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FinancistoTheme.dimens.RadiusTile))
            .clickable(onClick = onToggleExpand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconTile(icon = Icons.Rounded.Storefront, isActive = isExpanded)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (isExpanded) R.string.tx_label_payee_search else R.string.tx_label_payee),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isExpanded) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isExpanded) semantic.activeBorder else MaterialTheme.colorScheme.outline,
                )
                if (isExpanded) {
                    InlineSearchInputField(
                        value = query,
                        placeholder = stringResource(R.string.tx_placeholder_search_payee),
                        onValueChanged = onQueryChanged,
                    )
                } else {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (activePayeeName != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (activePayeeName != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ExpandableFieldTrailingActions(
            showClear = activePayeeName != null || query.isNotEmpty(),
            clearDescription = stringResource(R.string.tx_cd_clear_payee),
            isExpanded = isExpanded,
            collapsedActionIcon = Icons.Rounded.PersonAdd,
            onClear = onClear,
            onCollapse = onToggleExpand,
            onCollapsedAction = onToggleExpand,
        )
    }
}

@Composable
private fun PayeeSuggestionsSection(
    query: String,
    suggestions: List<PayeeOption>,
    selectedPayeeId: Long,
    showCreateButton: Boolean,
    onSelectPayee: (Long) -> Unit,
    onCreatePendingPayee: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = FinancistoTheme.semanticColors.heroBorder,
        )
        Text(
            text = stringResource(R.string.tx_header_suggestions),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        suggestions.forEach { payee ->
            SuggestionOptionRow(
                title = payee.title,
                tagLabel = stringResource(
                    if (payee.id == selectedPayeeId) R.string.tx_tag_selected else R.string.tx_tag_payee,
                ),
                isHighlighted = payee.id == selectedPayeeId,
                onClick = { onSelectPayee(payee.id) },
            )
        }
        if (showCreateButton) {
            Spacer(modifier = Modifier.height(2.dp))
            CreateInlineSuggestionButton(
                text = stringResource(R.string.tx_action_create_payee, query.trim()),
                onClick = { onCreatePendingPayee(query.trim()) },
            )
        }
    }
}

@Composable
fun LocationFieldCard(
    query: String,
    selectedLocationId: Long,
    locations: List<LocationOption>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSelectLocation: (Long) -> Unit,
    onClear: () -> Unit,
    onCreateNewLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTitle = remember(locations, selectedLocationId) {
        locations.firstOrNull { it.id == selectedLocationId }?.title
    } ?: stringResource(R.string.tx_placeholder_current_location)
    val savedSuggestions = remember(query, locations) {
        val savedLocations = locations.filter { it.id > 0L }
        filterAndRankSuggestions(savedLocations, query, MaxInlineSuggestions) { it.title }
    }

    FormFieldCardSurface(isActive = isExpanded, modifier = modifier) {
        LocationCardHeaderRow(
            query = query,
            selectedLocationId = selectedLocationId,
            selectedTitle = selectedTitle,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            onQueryChanged = onQueryChanged,
            onClear = onClear,
            onCreateNewLocation = onCreateNewLocation,
        )
        CapturedAnimatedVisibility(visible = isExpanded) {
            LocationSuggestionsSection(
                selectedLocationId = selectedLocationId,
                suggestions = savedSuggestions,
                onSelectLocation = onSelectLocation,
                onCreateNewLocation = onCreateNewLocation,
            )
        }
    }
}

@Composable
private fun LocationCardHeaderRow(
    query: String,
    selectedLocationId: Long,
    selectedTitle: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onCreateNewLocation: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FinancistoTheme.dimens.RadiusTile))
            .clickable(onClick = onToggleExpand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconTile(icon = Icons.Rounded.LocationOn, isActive = isExpanded)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isExpanded) R.string.tx_label_location_search else R.string.tx_label_location,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isExpanded) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isExpanded) semantic.activeBorder else MaterialTheme.colorScheme.outline,
                )
                if (isExpanded) {
                    InlineSearchInputField(
                        value = query,
                        placeholder = stringResource(R.string.tx_placeholder_search_location),
                        onValueChanged = onQueryChanged,
                    )
                } else {
                    Text(
                        text = selectedTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ExpandableFieldTrailingActions(
            showClear = selectedLocationId != TransactionIds.CURRENT_LOCATION_ID,
            clearDescription = stringResource(R.string.tx_cd_reset_location),
            isExpanded = isExpanded,
            collapsedActionIcon = Icons.Rounded.AddLocationAlt,
            onClear = onClear,
            onCollapse = onToggleExpand,
            onCollapsedAction = onCreateNewLocation,
        )
    }
}

@Composable
private fun ExpandableFieldTrailingActions(
    showClear: Boolean,
    clearDescription: String,
    isExpanded: Boolean,
    collapsedActionIcon: ImageVector,
    onClear: () -> Unit,
    onCollapse: () -> Unit,
    onCollapsedAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showClear) {
            ClearMiniIconButton(onClick = onClear, contentDescription = clearDescription)
        }
        if (isExpanded) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropUp,
                    contentDescription = stringResource(R.string.tx_cd_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AssistNewPill(
                label = stringResource(R.string.tx_action_new),
                icon = collapsedActionIcon,
                onClick = onCollapsedAction,
            )
        }
    }
}

@Composable
private fun LocationSuggestionsSection(
    selectedLocationId: Long,
    suggestions: List<LocationOption>,
    onSelectLocation: (Long) -> Unit,
    onCreateNewLocation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = FinancistoTheme.semanticColors.heroBorder,
        )
        SuggestionOptionRow(
            title = stringResource(R.string.tx_option_current_location_gps),
            tagLabel = stringResource(R.string.tx_tag_nearby),
            isHighlighted = selectedLocationId == TransactionIds.CURRENT_LOCATION_ID,
            icon = Icons.Rounded.MyLocation,
            onClick = { onSelectLocation(TransactionIds.CURRENT_LOCATION_ID) },
        )
        suggestions.forEach { loc ->
            SuggestionOptionRow(
                title = loc.title,
                tagLabel = stringResource(R.string.tx_tag_saved),
                isHighlighted = loc.id == selectedLocationId,
                onClick = { onSelectLocation(loc.id) },
            )
        }
        CreateInlineSuggestionButton(
            text = stringResource(R.string.tx_action_create_location),
            onClick = onCreateNewLocation,
        )
    }
}

@Composable
private fun InlineSearchInputField(
    value: String,
    placeholder: String,
    onValueChanged: (String) -> Unit,
) {
    val queryState = rememberSyncedTextFieldState(value, onValueChanged)
    BasicTextField(
        state = queryState,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(FinancistoTheme.semanticColors.activeBorder),
        textStyle = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        decorator = TextFieldDecorator { inner ->
            if (queryState.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            inner()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun NoteFieldCard(
    note: String,
    recentNotes: List<String>,
    onNoteChanged: (String) -> Unit,
    onSelectRecentNote: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormFieldCardSurface(isActive = false, modifier = modifier) {
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
                LeadingIconTile(icon = Icons.Rounded.EditNote)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tx_label_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    val noteState = rememberSyncedTextFieldState(note, onNoteChanged)
                    BasicTextField(
                        state = noteState,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        decorator = TextFieldDecorator { inner ->
                            if (noteState.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.tx_placeholder_add_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (note.isNotEmpty()) {
                ClearMiniIconButton(
                    onClick = onClear,
                    contentDescription = stringResource(R.string.tx_cd_clear_note),
                )
            }
        }
        if (recentNotes.isNotEmpty()) {
            RecentNotesChipRow(
                currentNote = note,
                recentNotes = recentNotes,
                onSelectRecentNote = onSelectRecentNote,
            )
        }
    }
}

@Composable
private fun RecentNotesChipRow(
    currentNote: String,
    recentNotes: List<String>,
    onSelectRecentNote: (String) -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = semantic.heroBorder)
    FlexBox(
        modifier = Modifier.fillMaxWidth(),
        config = {
            wrap(FlexWrap.Wrap)
            alignItems(FlexAlignItems.Center)
            gap(6.dp)
        },
    ) {
        Text(
            text = stringResource(R.string.tx_label_recent_notes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.outline,
        )
        recentNotes.forEach { recent ->
            FilterChip(
                selected = recent == currentNote,
                onClick = { onSelectRecentNote(recent) },
                label = {
                    Text(
                        text = recent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = semantic.pillSurface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = semantic.topChipBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = null,
            )
        }
    }
}

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

@Preview(name = "Payee Field Card - Expanded Light", showBackground = true)
@Composable
private fun PayeeFieldCardExpandedPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
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
}

@Preview(name = "Location & Note Cards - Dark", showBackground = true)
@Composable
private fun LocationAndNoteCardsDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
}
