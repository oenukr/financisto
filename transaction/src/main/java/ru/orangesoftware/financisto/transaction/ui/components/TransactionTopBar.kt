// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

private val MonthDayFormatterCache = ConcurrentHashMap<Locale, DateTimeFormatter>()
private val Time24FormatterCache = ConcurrentHashMap<Locale, DateTimeFormatter>()
private val Time12HmFormatterCache = ConcurrentHashMap<Locale, DateTimeFormatter>()
private val AmPmFormatterCache = ConcurrentHashMap<Locale, DateTimeFormatter>()

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
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val dateParts = remember(dateTimeMillis, locale, timeZone) {
        formatDateParts(dateTimeMillis, locale, timeZone)
    }
    val timeParts = remember(dateTimeMillis, is24HourFormat, locale, timeZone) {
        formatTimeParts(dateTimeMillis, is24HourFormat, locale, timeZone)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.tx_cd_back),
                tint = MaterialTheme.colorScheme.onSurface,
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
                actionLabel = stringResource(R.string.tx_cd_select_date),
                onClick = onDateClick,
            )
            TwoLineMetaChip(
                icon = Icons.Rounded.Schedule,
                lineOne = timeParts.first,
                lineTwo = timeParts.second,
                actionLabel = stringResource(R.string.tx_cd_select_time),
                onClick = onTimeClick,
            )
            StatusChip(status = status, onClick = onStatusClick)
        }
        TopBarOverflowMenu(
            onClearFormClick = onClearFormClick,
            onOpenLegacyClick = onOpenLegacyClick,
        )
    }
}

@Composable
private fun TopBarOverflowMenu(
    onClearFormClick: () -> Unit,
    onOpenLegacyClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.tx_cd_more_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tx_menu_clear_form)) },
                onClick = {
                    menuExpanded = false
                    onClearFormClick()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tx_menu_open_legacy)) },
                onClick = {
                    menuExpanded = false
                    onOpenLegacyClick()
                },
            )
        }
    }
}

@Composable
private fun TwoLineMetaChip(
    icon: ImageVector,
    lineOne: String,
    lineTwo: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val description = if (lineTwo.isNotEmpty()) "$actionLabel: $lineOne $lineTwo" else "$actionLabel: $lineOne"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dimens.RadiusPill),
        color = semantic.topChipBackground,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Column {
                Text(
                    text = lineOne,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (lineTwo.isNotEmpty()) {
                    Text(
                        text = lineTwo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val isClearedOrReconciled =
        status == TransactionStatusOption.CLEARED || status == TransactionStatusOption.RECONCILED
    val bgColor = if (isClearedOrReconciled) semantic.clearedContainer else semantic.topChipBackground
    val borderColor = if (isClearedOrReconciled) semantic.clearedBorder else semantic.heroBorder
    val iconColor = if (isClearedOrReconciled) semantic.clearedAccent else MaterialTheme.colorScheme.primary
    val textColor = if (isClearedOrReconciled) semantic.onClearedContainer else MaterialTheme.colorScheme.onSurface
    val labelText = status.localizedLabel()
    val statusDescription = "${stringResource(R.string.tx_cd_select_status)}: $labelText"

    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
        },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = bgColor,
            labelColor = textColor,
            leadingIconContentColor = iconColor,
        ),
        border = BorderStroke(dimens.DefaultBorderWidth, borderColor),
        modifier = Modifier.semantics { contentDescription = statusDescription },
    )
}

private fun formatDateParts(millis: Long, locale: Locale, timeZone: TimeZone): Pair<String, String> {
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
    val javaDate = LocalDate.of(ldt.year, ldt.month.number, ldt.day)
    val formatter = MonthDayFormatterCache.computeIfAbsent(locale) { loc ->
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(loc, "MMMd"), loc)
    }
    return formatter.format(javaDate) to ldt.year.toString()
}

private fun formatTimeParts(
    millis: Long,
    is24Hour: Boolean,
    locale: Locale,
    timeZone: TimeZone,
): Pair<String, String> {
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
    val javaTime = LocalTime.of(ldt.hour, ldt.minute)
    return if (is24Hour) {
        val formatter = Time24FormatterCache.computeIfAbsent(locale) { loc ->
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(loc, "Hm"), loc)
        }
        formatter.format(javaTime) to ""
    } else {
        val hmFormatter = Time12HmFormatterCache.computeIfAbsent(locale) { loc ->
            DateTimeFormatter.ofPattern("h:mm", loc)
        }
        val amPmFormatter = AmPmFormatterCache.computeIfAbsent(locale) { loc ->
            DateTimeFormatter.ofPattern("a", loc)
        }
        hmFormatter.format(javaTime) to amPmFormatter.format(javaTime)
    }
}

@Preview(name = "Transaction Top Bar - Cleared Light", showBackground = true)
@Composable
private fun TransactionTopBarClearedPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
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
}

@Preview(name = "Transaction Top Bar - Unreconciled Dark", showBackground = true)
@Composable
private fun TransactionTopBarUnreconciledDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
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
}
