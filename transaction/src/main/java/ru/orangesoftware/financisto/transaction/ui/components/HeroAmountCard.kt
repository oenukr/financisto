// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.TransactionMode

@Composable
fun HeroAmountCard(
    model: HeroAmountDisplayModel,
    onSwitchMode: (TransactionMode) -> Unit,
    onCycleSignOrMode: () -> Unit,
    onPrimaryAmountChanged: (String) -> Unit,
    onSecondaryAmountChanged: (String) -> Unit,
    onOpenPrimaryCalculator: () -> Unit,
    onOpenSecondaryCalculator: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val isEnabled = enabled && model.enabled

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.RadiusHeroCard),
        color = semantic.heroSurface,
        border = BorderStroke(dimens.DefaultBorderWidth, semantic.heroBorder),
    ) {
        Column(modifier = Modifier.padding(dimens.HeroPadding)) {
            HeroCardHeaderRow(model = model, enabled = isEnabled, onSwitchMode = onSwitchMode)
            Spacer(modifier = Modifier.height(12.dp))
            if (model.isDualCurrency) {
                DualCurrencyAmountRows(
                    model = model,
                    enabled = isEnabled,
                    onPrimaryAmountChanged = onPrimaryAmountChanged,
                    onSecondaryAmountChanged = onSecondaryAmountChanged,
                    onCycleSignOrMode = onCycleSignOrMode,
                    onOpenPrimaryCalculator = onOpenPrimaryCalculator,
                    onOpenSecondaryCalculator = onOpenSecondaryCalculator,
                )
            } else {
                SingleAmountRow(
                    symbol = model.primaryCurrencySymbol,
                    amountInput = model.primaryInput,
                    enabled = isEnabled,
                    onAmountChanged = onPrimaryAmountChanged,
                    onCycleSignOrMode = onCycleSignOrMode,
                    onOpenCalculator = onOpenPrimaryCalculator,
                )
            }
        }
    }
}

@Composable
private fun HeroCardHeaderRow(
    model: HeroAmountDisplayModel,
    enabled: Boolean,
    onSwitchMode: (TransactionMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeSelectorBadge(
            mode = model.mode,
            isDualCurrency = model.isDualCurrency,
            enabled = enabled,
            onSwitchMode = onSwitchMode,
        )
        val rightLabel = when {
            model.isDualCurrency -> stringResource(R.string.tx_label_dual_currency_mode)
            model.mode == TransactionMode.TRANSFER ->
                stringResource(R.string.tx_label_amount_out_currency, model.primaryCurrencyCode)
            else -> stringResource(R.string.tx_label_amount_currency, model.primaryCurrencyCode)
        }
        Text(
            text = rightLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeSelectorBadge(
    mode: TransactionMode,
    isDualCurrency: Boolean,
    enabled: Boolean,
    onSwitchMode: (TransactionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    val (bgColor, textColor, label) = resolveModeBadgeStyle(mode, isDualCurrency)
    val leadingIcon: (@Composable () -> Unit)? = if (mode == TransactionMode.TRANSFER) {
        {
            Icon(
                imageVector = if (isDualCurrency) Icons.Rounded.CurrencyExchange else Icons.Rounded.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    } else {
        null
    }

    Box {
        AssistChip(
            onClick = { expanded = true },
            enabled = enabled,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            leadingIcon = leadingIcon,
            shape = CircleShape,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = bgColor,
                labelColor = textColor,
                leadingIconContentColor = textColor,
            ),
            border = null,
        )
        ModeSelectorDropdown(
            expanded = expanded && enabled,
            enabled = enabled,
            onDismiss = { expanded = false },
            onSelectMode = { selected ->
                expanded = false
                onSwitchMode(selected)
            },
        )
    }
}

@Composable
private fun resolveModeBadgeStyle(
    mode: TransactionMode,
    isDualCurrency: Boolean,
): Triple<Color, Color, String> {
    val semantic = FinancistoTheme.semanticColors
    return when {
        mode == TransactionMode.TRANSFER && isDualCurrency -> Triple(
            semantic.transferContainer,
            semantic.onTransferContainer,
            stringResource(R.string.tx_mode_currency_transfer),
        )
        mode == TransactionMode.TRANSFER -> Triple(
            semantic.transferContainer,
            semantic.onTransferContainer,
            stringResource(R.string.tx_mode_transfer),
        )
        mode == TransactionMode.INCOME -> Triple(
            semantic.incomeContainer,
            semantic.onIncomeContainer,
            stringResource(R.string.tx_mode_income),
        )
        else -> Triple(
            semantic.expenseContainer,
            semantic.onExpenseContainer,
            stringResource(R.string.tx_mode_expense),
        )
    }
}

@Composable
private fun ModeSelectorDropdown(
    expanded: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelectMode: (TransactionMode) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tx_mode_expense)) },
            enabled = enabled,
            onClick = { onSelectMode(TransactionMode.EXPENSE) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tx_mode_income)) },
            enabled = enabled,
            onClick = { onSelectMode(TransactionMode.INCOME) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tx_mode_transfer_menu)) },
            enabled = enabled,
            onClick = { onSelectMode(TransactionMode.TRANSFER) },
        )
    }
}

@Composable
private fun SingleAmountRow(
    symbol: String,
    amountInput: String,
    enabled: Boolean,
    onAmountChanged: (String) -> Unit,
    onCycleSignOrMode: () -> Unit,
    onOpenCalculator: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (symbol.isNotBlank()) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            val amountState = rememberSyncedTextFieldState(amountInput, onAmountChanged)
            BasicTextField(
                state = amountState,
                enabled = enabled,
                inputTransformation = DecimalInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AmountRowActionButtons(
            toggleIcon = Icons.Rounded.UnfoldMore,
            enabled = enabled,
            onToggleClick = onCycleSignOrMode,
            onCalcClick = onOpenCalculator,
        )
    }
}

@Composable
private fun DualCurrencyAmountRows(
    model: HeroAmountDisplayModel,
    enabled: Boolean,
    onPrimaryAmountChanged: (String) -> Unit,
    onSecondaryAmountChanged: (String) -> Unit,
    onCycleSignOrMode: () -> Unit,
    onOpenPrimaryCalculator: () -> Unit,
    onOpenSecondaryCalculator: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val isTransfer = model.mode == TransactionMode.TRANSFER
    val primaryHeader = stringResource(
        if (isTransfer) R.string.tx_label_amount_out_debit else R.string.tx_label_foreign_amount,
    )
    val secondaryHeader = stringResource(
        if (isTransfer) R.string.tx_label_amount_in_credit else R.string.tx_label_account_amount,
    )
    val isPrimaryPositive = model.mode == TransactionMode.INCOME
    val isSecondaryPositive = isTransfer || model.mode == TransactionMode.INCOME
    val primarySignColor = if (isPrimaryPositive) semantic.onIncomeContainer else semantic.onDebitContainer
    val secondarySignColor = if (isSecondaryPositive) semantic.onIncomeContainer else semantic.onDebitContainer

    Column {
        DualAmountLineHeader(primarySignColor, primaryHeader, model.primaryAccountLabel)
        Spacer(modifier = Modifier.height(4.dp))
        DualAmountInputRow(
            sign = if (isPrimaryPositive) "+" else "−",
            signColor = primarySignColor,
            input = model.primaryInput,
            currencyCode = model.primaryCurrencyCode,
            enabled = enabled,
            onValueChange = onPrimaryAmountChanged,
            trailingIcon = Icons.Rounded.SwapVert,
            onTrailingClick = onCycleSignOrMode,
            onCalcClick = onOpenPrimaryCalculator,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = semantic.heroBorder)
        DualAmountLineHeader(secondarySignColor, secondaryHeader, model.secondaryAccountLabel)
        Spacer(modifier = Modifier.height(4.dp))
        DualAmountInputRow(
            sign = if (isSecondaryPositive) "+" else "−",
            signColor = secondarySignColor,
            input = model.secondaryInput,
            currencyCode = model.secondaryCurrencyCode,
            enabled = enabled,
            onValueChange = onSecondaryAmountChanged,
            trailingIcon = Icons.Rounded.UnfoldMore,
            onTrailingClick = onCycleSignOrMode,
            onCalcClick = onOpenSecondaryCalculator,
        )
    }
}

@Composable
private fun DualAmountLineHeader(
    dotColor: Color,
    header: String,
    accountLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = accountLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun DualAmountInputRow(
    sign: String,
    signColor: Color,
    input: String,
    currencyCode: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    trailingIcon: ImageVector,
    onTrailingClick: () -> Unit,
    onCalcClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sign,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = signColor,
                modifier = Modifier.padding(end = 6.dp),
            )
            val inputState = rememberSyncedTextFieldState(input, onValueChange)
            BasicTextField(
                state = inputState,
                enabled = enabled,
                inputTransformation = DecimalInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = currencyCode,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        AmountRowActionButtons(
            toggleIcon = trailingIcon,
            enabled = enabled,
            onToggleClick = onTrailingClick,
            onCalcClick = onCalcClick,
        )
    }
}

@Composable
private fun AmountRowActionButtons(
    toggleIcon: ImageVector,
    enabled: Boolean,
    onToggleClick: () -> Unit,
    onCalcClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleClick, enabled = enabled) {
            Icon(
                imageVector = toggleIcon,
                contentDescription = stringResource(R.string.tx_cd_toggle_expense_income),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedIconButton(
            onClick = onCalcClick,
            enabled = enabled,
            shape = RoundedCornerShape(dimens.RadiusTile),
            border = BorderStroke(dimens.DefaultBorderWidth, semantic.cardBorder),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Calculate,
                contentDescription = stringResource(R.string.tx_cd_open_calculator),
            )
        }
    }
}

@Preview(name = "Hero Amount Card - Single Light", showBackground = true)
@Composable
private fun HeroAmountCardSingleCurrencyPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                HeroAmountCard(
                    model = HeroAmountDisplayModel(
                        mode = TransactionMode.EXPENSE,
                        isDualCurrency = false,
                        primaryInput = "125.00",
                        primaryCurrencyCode = "USD",
                        primaryCurrencySymbol = "$",
                        primaryAccountLabel = "Main Checking",
                        secondaryInput = "",
                        secondaryCurrencyCode = "",
                        secondaryCurrencySymbol = "",
                        secondaryAccountLabel = "",
                    ),
                    onSwitchMode = {},
                    onCycleSignOrMode = {},
                    onPrimaryAmountChanged = {},
                    onSecondaryAmountChanged = {},
                    onOpenPrimaryCalculator = {},
                    onOpenSecondaryCalculator = {},
                )
            }
        }
    }
}

@Preview(name = "Hero Amount Card - Dual Dark", showBackground = true)
@Composable
private fun HeroAmountCardDualCurrencyDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                HeroAmountCard(
                    model = HeroAmountDisplayModel(
                        mode = TransactionMode.TRANSFER,
                        isDualCurrency = true,
                        primaryInput = "100.00",
                        primaryCurrencyCode = "USD",
                        primaryCurrencySymbol = "$",
                        primaryAccountLabel = "From Main Checking",
                        secondaryInput = "92.50",
                        secondaryCurrencyCode = "EUR",
                        secondaryCurrencySymbol = "€",
                        secondaryAccountLabel = "To Travel Card",
                    ),
                    onSwitchMode = {},
                    onCycleSignOrMode = {},
                    onPrimaryAmountChanged = {},
                    onSecondaryAmountChanged = {},
                    onOpenPrimaryCalculator = {},
                    onOpenSecondaryCalculator = {},
                )
            }
        }
    }
}
