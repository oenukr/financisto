// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionTopBar(
    dateTimeMillis: Long,
    is24HourFormat: Boolean,
    status: TransactionStatusOption,
    onBackClick: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onStatusClick: () -> Unit,
    onClearFormClick: () -> Unit,
    onOpenLegacyClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateParts = remember(dateTimeMillis) { formatDateParts(dateTimeMillis) }
    val timeParts = remember(dateTimeMillis, is24HourFormat) {
        formatTimeParts(dateTimeMillis, is24HourFormat)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = StitchColors.TextPrimary,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TwoLineMetaChip(
                icon = Icons.Rounded.CalendarToday,
                lineOne = dateParts.first,
                lineTwo = dateParts.second,
                onClick = onDateClick,
            )
            TwoLineMetaChip(
                icon = Icons.Rounded.Schedule,
                lineOne = timeParts.first,
                lineTwo = timeParts.second,
                onClick = onTimeClick,
            )
            StatusChip(status = status, onClick = onStatusClick)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options",
                    tint = StitchColors.TextPrimary,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Clear form") },
                    onClick = {
                        menuExpanded = false
                        onClearFormClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Open in legacy editor") },
                    onClick = {
                        menuExpanded = false
                        onOpenLegacyClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun TwoLineMetaChip(
    icon: ImageVector,
    lineOne: String,
    lineTwo: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(StitchDimens.RadiusPill),
        color = StitchColors.TopChipBg,
        modifier = Modifier
            .clip(RoundedCornerShape(StitchDimens.RadiusPill))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StitchColors.Primary,
                modifier = Modifier.size(16.dp),
            )
            Column {
                Text(
                    text = lineOne,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StitchColors.TextPrimary,
                    lineHeight = 12.sp,
                    maxLines = 1,
                )
                if (lineTwo.isNotEmpty()) {
                    Text(
                        text = lineTwo,
                        fontSize = 10.sp,
                        color = StitchColors.TextSecondary,
                        lineHeight = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: TransactionStatusOption,
    onClick: () -> Unit,
) {
    val isClearedOrReconciled =
        status == TransactionStatusOption.CLEARED || status == TransactionStatusOption.RECONCILED
    val bgColor = if (isClearedOrReconciled) StitchColors.ClearedBg else StitchColors.TopChipBg
    val borderColor = if (isClearedOrReconciled) StitchColors.ClearedBorder else StitchColors.HeroBorder
    val iconColor = if (isClearedOrReconciled) StitchColors.ClearedGreen else StitchColors.Primary
    val textColor = if (isClearedOrReconciled) StitchColors.ClearedText else StitchColors.TextPrimary

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, borderColor),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = status.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatDateParts(millis: Long): Pair<String, String> {
    val date = Date(millis)
    val monthDay = SimpleDateFormat("MMM d,", Locale.getDefault()).format(date)
    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
    return monthDay to year
}

private fun formatTimeParts(millis: Long, is24Hour: Boolean): Pair<String, String> {
    val date = Date(millis)
    return if (is24Hour) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date) to ""
    } else {
        val hm = SimpleDateFormat("h:mm", Locale.getDefault()).format(date)
        val amPm = SimpleDateFormat("a", Locale.getDefault()).format(date)
        hm to amPm
    }
}

@Preview(name = "Transaction Top Bar - Cleared", showBackground = true)
@Composable
private fun TransactionTopBarClearedPreview() {
    TransactionStitchTheme {
        TransactionTopBar(
            dateTimeMillis = 1715774400000L,
            is24HourFormat = false,
            status = TransactionStatusOption.CLEARED,
            onBackClick = {},
            onDateClick = {},
            onTimeClick = {},
            onStatusClick = {},
            onClearFormClick = {},
            onOpenLegacyClick = {},
        )
    }
}

@Preview(name = "Transaction Top Bar - Unreconciled", showBackground = true)
@Composable
private fun TransactionTopBarUnreconciledPreview() {
    TransactionStitchTheme {
        TransactionTopBar(
            dateTimeMillis = 1715774400000L,
            is24HourFormat = true,
            status = TransactionStatusOption.UR,
            onBackClick = {},
            onDateClick = {},
            onTimeClick = {},
            onStatusClick = {},
            onClearFormClick = {},
            onOpenLegacyClick = {},
        )
    }
}

