// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    title: String,
    accounts: List<AccountOption>,
    selectedAccountId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        AccountPickerSheetContent(
            title = title,
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            onSelect = onSelect,
        )
    }
}

@Composable
fun AccountPickerSheetContent(
    title: String,
    accounts: List<AccountOption>,
    selectedAccountId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queryState = rememberTextFieldState()
    val filtered by remember(accounts) {
        derivedStateOf {
            val needle = queryState.text.toString().trim().lowercase()
            if (needle.isEmpty()) accounts else accounts.filter { it.title.lowercase().contains(needle) }
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            state = queryState,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text(stringResource(R.string.tx_placeholder_search_account)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(filtered, key = { it.id }) { acc ->
                val bal = AmountScale.formatDisplayAmount(
                    minorUnits = acc.balanceMinor,
                    decimals = acc.currencyDecimals,
                    symbol = acc.currencySymbol.ifBlank { acc.currencyCode },
                    includeSign = acc.balanceMinor < 0L,
                )
                SelectableSheetRow(
                    title = if (acc.isActive) {
                        acc.title
                    } else {
                        stringResource(R.string.tx_account_archived_format, acc.title)
                    },
                    subtitle = stringResource(R.string.tx_account_meta_format, acc.typeLabel, bal),
                    selected = acc.id == selectedAccountId,
                    onClick = { onSelect(acc.id) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    currencies: List<CurrencyOption>,
    accountCurrencyCode: String,
    selectedOriginalCurrencyId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        CurrencyPickerSheetContent(
            currencies = currencies,
            accountCurrencyCode = accountCurrencyCode,
            selectedOriginalCurrencyId = selectedOriginalCurrencyId,
            onSelect = onSelect,
        )
    }
}

@Composable
fun CurrencyPickerSheetContent(
    currencies: List<CurrencyOption>,
    accountCurrencyCode: String,
    selectedOriginalCurrencyId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.tx_sheet_transaction_currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectableSheetRow(
            title = stringResource(R.string.tx_currency_same_as_account_code, accountCurrencyCode),
            subtitle = stringResource(R.string.tx_currency_no_conversion),
            selected = selectedOriginalCurrencyId == null,
            onClick = { onSelect(null) },
        )
        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
            items(currencies, key = { it.id }) { currency ->
                SelectableSheetRow(
                    title = stringResource(R.string.tx_currency_code_symbol_format, currency.code, currency.symbol),
                    subtitle = pluralStringResource(
                        R.plurals.tx_currency_decimals,
                        currency.decimals,
                        currency.decimals,
                    ),
                    selected = selectedOriginalCurrencyId == currency.id,
                    onClick = { onSelect(currency.id) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPickerSheet(
    selectedStatus: TransactionStatusOption,
    onSelect: (TransactionStatusOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        StatusPickerSheetContent(
            selectedStatus = selectedStatus,
            onSelect = onSelect,
        )
    }
}

@Composable
fun StatusPickerSheetContent(
    selectedStatus: TransactionStatusOption,
    onSelect: (TransactionStatusOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.tx_sheet_transaction_status),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TransactionStatusOption.entries.forEach { option ->
            SelectableSheetRow(
                title = option.localizedLabel(),
                subtitle = "",
                selected = option == selectedStatus,
                onClick = { onSelect(option) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SelectableSheetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile),
        color = if (selected) semantic.topChipBackground else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val SamplePickerAccounts = listOf(
    AccountOption(
        id = 1L,
        title = "Main Checking",
        typeLabel = "Bank",
        currencyId = 1L,
        currencyCode = "USD",
        currencySymbol = "$",
        currencyDecimals = 2,
        balanceMinor = 245075L,
    ),
    AccountOption(
        id = 2L,
        title = "Travel Card",
        typeLabel = "Credit Card",
        currencyId = 2L,
        currencyCode = "EUR",
        currencySymbol = "€",
        currencyDecimals = 2,
        balanceMinor = -42000L,
    ),
)

private val SamplePickerCurrencies = listOf(
    CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
    CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
)

@Preview(name = "Account Picker Sheet - Light", showBackground = true)
@Composable
private fun AccountPickerSheetPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            AccountPickerSheetContent(
                title = "Select Account",
                accounts = SamplePickerAccounts,
                selectedAccountId = 1L,
                onSelect = {},
            )
        }
    }
}

@Preview(name = "Currency & Status Pickers - Dark", showBackground = true)
@Composable
private fun CurrencyAndStatusPickerDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                CurrencyPickerSheetContent(
                    currencies = SamplePickerCurrencies,
                    accountCurrencyCode = "USD",
                    selectedOriginalCurrencyId = 2L,
                    onSelect = {},
                )
                StatusPickerSheetContent(
                    selectedStatus = TransactionStatusOption.CLEARED,
                    onSelect = {},
                )
            }
        }
    }
}
