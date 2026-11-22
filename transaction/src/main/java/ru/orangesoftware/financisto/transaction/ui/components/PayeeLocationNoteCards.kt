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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.model.LocationOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

private const val MAX_INLINE_SUGGESTIONS = 5

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
) {
    val activePayeeName = payees.firstOrNull { it.id == selectedPayeeId }?.title ?: pendingPayeeName
    val displayValue = activePayeeName ?: query.ifBlank { "No payee" }
    val filtered = remember(query, payees) {
        val needle = query.trim().lowercase()
        val source = if (needle.isEmpty()) payees else payees.filter { it.title.lowercase().contains(needle) }
        source.take(MAX_INLINE_SUGGESTIONS)
    }
    val hasExactMatch = payees.any { it.title.equals(query.trim(), ignoreCase = true) }

    FormFieldCardSurface(isActive = isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                        text = if (isExpanded) "Payee (Incremental search)" else "Payee",
                        fontSize = 11.sp,
                        fontWeight = if (isExpanded) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isExpanded) StitchColors.ActiveBorder else StitchColors.TextTertiary,
                    )
                    if (isExpanded) {
                        val queryState = rememberSyncedTextFieldState(query, onQueryChanged)
                        BasicTextField(
                            state = queryState,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            cursorBrush = SolidColor(StitchColors.ActiveBorder),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = StitchColors.TextPrimary,
                            ),
                            decorator = TextFieldDecorator { inner ->
                                if (queryState.text.isEmpty()) {
                                    Text(
                                        text = "Search or enter payee...",
                                        fontSize = 14.sp,
                                        color = StitchColors.TextTertiary,
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = displayValue,
                            fontSize = 14.sp,
                            fontWeight = if (activePayeeName != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (activePayeeName != null) {
                                StitchColors.TextPrimary
                            } else {
                                StitchColors.TextSecondary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (activePayeeName != null || query.isNotEmpty()) {
                    ClearMiniIconButton(onClick = onClear, contentDescription = "Clear payee")
                }
                if (isExpanded) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropUp,
                            contentDescription = "Collapse",
                            tint = StitchColors.TextSecondary,
                        )
                    }
                } else {
                    AssistNewPill(label = "New", icon = Icons.Rounded.PersonAdd, onClick = onToggleExpand)
                }
            }
        }
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
private fun PayeeSuggestionsSection(
    query: String,
    suggestions: List<PayeeOption>,
    selectedPayeeId: Long,
    showCreateButton: Boolean,
    onSelectPayee: (Long) -> Unit,
    onCreatePendingPayee: (String) -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = StitchColors.HeroBorder)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "SUGGESTIONS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = StitchColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        suggestions.forEach { payee ->
            SuggestionOptionRow(
                title = payee.title,
                tagLabel = if (payee.id == selectedPayeeId) "Selected" else "Payee",
                isHighlighted = payee.id == selectedPayeeId,
                onClick = { onSelectPayee(payee.id) },
            )
        }
        if (showCreateButton) {
            Spacer(modifier = Modifier.height(2.dp))
            CreateInlineSuggestionButton(
                text = "+ Create new payee \"${query.trim()}\"",
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
) {
    val selectedTitle = locations.firstOrNull { it.id == selectedLocationId }?.title
        ?: "Current location"
    val filtered = remember(query, locations) {
        val needle = query.trim().lowercase()
        val source = if (needle.isEmpty()) locations else locations.filter { it.title.lowercase().contains(needle) }
        source.take(MAX_INLINE_SUGGESTIONS)
    }

    FormFieldCardSurface(isActive = isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                        text = if (isExpanded) "Location (Incremental search)" else "Location",
                        fontSize = 11.sp,
                        fontWeight = if (isExpanded) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isExpanded) StitchColors.ActiveBorder else StitchColors.TextTertiary,
                    )
                    if (isExpanded) {
                        val queryState = rememberSyncedTextFieldState(query, onQueryChanged)
                        BasicTextField(
                            state = queryState,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            cursorBrush = SolidColor(StitchColors.ActiveBorder),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = StitchColors.TextPrimary,
                            ),
                            decorator = TextFieldDecorator { inner ->
                                if (queryState.text.isEmpty()) {
                                    Text("Search location...", fontSize = 14.sp, color = StitchColors.TextTertiary)
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = selectedTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selectedLocationId != TransactionIds.CURRENT_LOCATION_ID) {
                    ClearMiniIconButton(onClick = onClear, contentDescription = "Reset location")
                }
                if (isExpanded) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropUp,
                            contentDescription = "Collapse",
                            tint = StitchColors.TextSecondary,
                        )
                    }
                } else {
                    AssistNewPill(label = "New", icon = Icons.Rounded.AddLocationAlt, onClick = onCreateNewLocation)
                }
            }
        }
        CapturedAnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = StitchColors.HeroBorder)
                SuggestionOptionRow(
                    title = "Current location (GPS)",
                    tagLabel = "Nearby",
                    isHighlighted = selectedLocationId == TransactionIds.CURRENT_LOCATION_ID,
                    icon = Icons.Rounded.MyLocation,
                    onClick = { onSelectLocation(TransactionIds.CURRENT_LOCATION_ID) },
                )
                filtered.filter { it.id > 0L }.forEach { loc ->
                    SuggestionOptionRow(
                        title = loc.title,
                        tagLabel = "Saved",
                        isHighlighted = loc.id == selectedLocationId,
                        onClick = { onSelectLocation(loc.id) },
                    )
                }
                CreateInlineSuggestionButton(
                    text = "+ Create new location in editor",
                    onClick = onCreateNewLocation,
                )
            }
        }
    }
}

@Composable
fun NoteFieldCard(
    note: String,
    recentNotes: List<String>,
    onNoteChanged: (String) -> Unit,
    onSelectRecentNote: (String) -> Unit,
    onClear: () -> Unit,
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
                LeadingIconTile(icon = Icons.Rounded.EditNote)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = StitchColors.TextTertiary,
                    )
                    val noteState = rememberSyncedTextFieldState(note, onNoteChanged)
                    BasicTextField(
                        state = noteState,
                        cursorBrush = SolidColor(StitchColors.Primary),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchColors.TextPrimary,
                        ),
                        decorator = TextFieldDecorator { inner ->
                            if (noteState.text.isEmpty()) {
                                Text("Add a note...", fontSize = 13.sp, color = StitchColors.TextTertiary)
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (note.isNotEmpty()) {
                ClearMiniIconButton(onClick = onClear, contentDescription = "Clear note")
            }
        }
        if (recentNotes.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = StitchColors.HeroBorder)
            FlexBox(
                modifier = Modifier.fillMaxWidth(),
                config = {
                    wrap(FlexWrap.Wrap)
                    alignItems(FlexAlignItems.Center)
                    gap(6.dp)
                },
            ) {
                Text(
                    text = "Recent:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchColors.TextTertiary,
                )
                recentNotes.forEach { recent ->
                    Surface(
                        shape = CircleShape,
                        color = if (recent == note) StitchColors.TopChipBg else StitchColors.PillSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onSelectRecentNote(recent) },
                    ) {
                        Text(
                            text = recent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = StitchColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private val samplePayees = listOf(
    PayeeOption(id = 1L, title = "Supermarket Grocery"),
    PayeeOption(id = 2L, title = "Corner Coffee Shop"),
    PayeeOption(id = 3L, title = "Gas Station & Auto"),
)

@Preview(name = "Payee Field Card - Collapsed", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun PayeeFieldCardCollapsedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PayeeFieldCard(
                query = "",
                selectedPayeeId = 1L,
                pendingPayeeName = null,
                payees = samplePayees,
                isExpanded = false,
                onToggleExpand = {},
                onQueryChanged = {},
                onSelectPayee = {},
                onCreatePendingPayee = {},
                onClear = {},
            )
        }
    }
}

@Preview(name = "Payee Field Card - Expanded", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun PayeeFieldCardExpandedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PayeeFieldCard(
                query = "Coffee",
                selectedPayeeId = 0L,
                pendingPayeeName = null,
                payees = samplePayees,
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

private val sampleLocations = listOf(
    LocationOption(id = 1L, title = "Main Office"),
    LocationOption(id = 2L, title = "Downtown Mall"),
    LocationOption(id = 3L, title = "Home Town"),
)

@Preview(name = "Location Field Card - Collapsed", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun LocationFieldCardCollapsedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            LocationFieldCard(
                query = "",
                selectedLocationId = 1L,
                locations = sampleLocations,
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

@Preview(name = "Location Field Card - Expanded", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun LocationFieldCardExpandedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            LocationFieldCard(
                query = "Mall",
                selectedLocationId = TransactionIds.CURRENT_LOCATION_ID,
                locations = sampleLocations,
                isExpanded = true,
                onToggleExpand = {},
                onQueryChanged = {},
                onSelectLocation = {},
                onClear = {},
                onCreateNewLocation = {},
            )
        }
    }
}

private val sampleRecentNotes = listOf("Weekly groceries", "Gas station", "Dinner with team")

@Preview(name = "Note Field Card - Filled with Recent Notes", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun NoteFieldCardPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            NoteFieldCard(
                note = "Weekly groceries",
                recentNotes = sampleRecentNotes,
                onNoteChanged = {},
                onSelectRecentNote = {},
                onClear = {},
            )
        }
    }
}

@Preview(name = "Note Field Card - Empty", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun NoteFieldCardEmptyPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            NoteFieldCard(
                note = "",
                recentNotes = sampleRecentNotes,
                onNoteChanged = {},
                onSelectRecentNote = {},
                onClear = {},
            )
        }
    }
}
