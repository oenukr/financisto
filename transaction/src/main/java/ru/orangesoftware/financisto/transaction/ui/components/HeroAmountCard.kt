// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

data class HeroAmountDisplayModel(
    val mode: TransactionMode,
    val isDualCurrency: Boolean,
    val primaryInput: String,
    val primaryCurrencyCode: String,
    val primaryCurrencySymbol: String,
    val primaryAccountLabel: String,
    val secondaryInput: String,
    val secondaryCurrencyCode: String,
    val secondaryCurrencySymbol: String,
    val secondaryAccountLabel: String,
)

@Composable
fun HeroAmountCard(
    model: HeroAmountDisplayModel,
    onSwitchMode: (TransactionMode) -> Unit,
    onCycleSignOrMode: () -> Unit,
    onPrimaryAmountChanged: (String) -> Unit,
    onSecondaryAmountChanged: (String) -> Unit,
    onOpenPrimaryCalculator: () -> Unit,
    onOpenSecondaryCalculator: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StitchDimens.RadiusHeroCard),
        color = StitchColors.HeroSurface,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
    ) {
        Column(modifier = Modifier.padding(StitchDimens.HeroPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSelectorBadge(
                    mode = model.mode,
                    isDualCurrency = model.isDualCurrency,
                    onSwitchMode = onSwitchMode,
                )
                val rightLabel = when {
                    model.isDualCurrency -> "DUAL-CURRENCY MODE"
                    model.mode == TransactionMode.TRANSFER -> "AMOUNT OUT (${model.primaryCurrencyCode})"
                    else -> "AMOUNT (${model.primaryCurrencyCode})"
                }
                Text(
                    text = rightLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = StitchColors.TextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (model.isDualCurrency) {
                DualCurrencyAmountRows(
                    model = model,
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
                    onAmountChanged = onPrimaryAmountChanged,
                    onCycleSignOrMode = onCycleSignOrMode,
                    onOpenCalculator = onOpenPrimaryCalculator,
                )
            }
        }
    }
}

@Composable
private fun ModeSelectorBadge(
    mode: TransactionMode,
    isDualCurrency: Boolean,
    onSwitchMode: (TransactionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val (bgColor, textColor, label) = when {
        mode == TransactionMode.TRANSFER && isDualCurrency ->
            Triple(StitchColors.TransferBadgeBg, StitchColors.TransferBadgeText, "Currency Transfer")
        mode == TransactionMode.TRANSFER ->
            Triple(StitchColors.TransferBadgeBg, StitchColors.TransferBadgeText, "Transfer")
        mode == TransactionMode.INCOME ->
            Triple(StitchColors.IncomeBadgeBg, StitchColors.IncomeBadgeText, "+ Income")
        else ->
            Triple(StitchColors.ExpenseBadgeBg, StitchColors.ExpenseBadgeText, "– Expense")
    }
    Box {
        Surface(
            shape = CircleShape,
            color = bgColor,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (mode == TransactionMode.TRANSFER) {
                    Icon(
                        imageVector = if (isDualCurrency) {
                            Icons.Rounded.CurrencyExchange
                        } else {
                            Icons.Rounded.SwapHoriz
                        },
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("– Expense") },
                onClick = {
                    expanded = false
                    onSwitchMode(TransactionMode.EXPENSE)
                },
            )
            DropdownMenuItem(
                text = { Text("+ Income") },
                onClick = {
                    expanded = false
                    onSwitchMode(TransactionMode.INCOME)
                },
            )
            DropdownMenuItem(
                text = { Text("⇄ Transfer") },
                onClick = {
                    expanded = false
                    onSwitchMode(TransactionMode.TRANSFER)
                },
            )
        }
    }
}

@Composable
private fun SingleAmountRow(
    symbol: String,
    amountInput: String,
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = StitchColors.TextPrimary,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            val amountState = rememberSyncedTextFieldState(amountInput, onAmountChanged)
            BasicTextField(
                state = amountState,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(StitchColors.Primary),
                textStyle = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StitchColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onCycleSignOrMode, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.UnfoldMore,
                    contentDescription = "Toggle expense or income",
                    tint = StitchColors.TextSecondary,
                )
            }
            Surface(
                shape = RoundedCornerShape(StitchDimens.RadiusTile),
                color = StitchColors.SurfaceWhite,
                border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.CardBorder),
            ) {
                IconButton(onClick = onOpenCalculator, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Calculate,
                        contentDescription = "Open calculator",
                        tint = StitchColors.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DualCurrencyAmountRows(
    model: HeroAmountDisplayModel,
    onPrimaryAmountChanged: (String) -> Unit,
    onSecondaryAmountChanged: (String) -> Unit,
    onCycleSignOrMode: () -> Unit,
    onOpenPrimaryCalculator: () -> Unit,
    onOpenSecondaryCalculator: () -> Unit,
) {
    val isTransfer = model.mode == TransactionMode.TRANSFER
    val primaryHeader = if (isTransfer) "AMOUNT OUT (DEBIT)" else "FOREIGN AMOUNT"
    val secondaryHeader = if (isTransfer) "AMOUNT IN (CREDIT)" else "ACCOUNT AMOUNT"
    val primarySign = if (model.mode == TransactionMode.INCOME) "+" else "−"
    val secondarySign = if (isTransfer || model.mode == TransactionMode.INCOME) "+" else "−"
    val primarySignColor = if (model.mode == TransactionMode.INCOME) {
        StitchColors.IncomeBadgeText
    } else {
        StitchColors.DebitText
    }
    val secondarySignColor = if (isTransfer || model.mode == TransactionMode.INCOME) {
        StitchColors.IncomeBadgeText
    } else {
        StitchColors.DebitText
    }

    Column {
        DualAmountLine(
            dotColor = primarySignColor,
            header = primaryHeader,
            accountLabel = model.primaryAccountLabel,
            sign = primarySign,
            signColor = primarySignColor,
            input = model.primaryInput,
            currencyCode = model.primaryCurrencyCode,
            onValueChange = onPrimaryAmountChanged,
            trailingIcon = Icons.Rounded.SwapVert,
            onTrailingClick = onCycleSignOrMode,
            onCalcClick = onOpenPrimaryCalculator,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = StitchColors.HeroBorder,
        )
        DualAmountLine(
            dotColor = secondarySignColor,
            header = secondaryHeader,
            accountLabel = model.secondaryAccountLabel,
            sign = secondarySign,
            signColor = secondarySignColor,
            input = model.secondaryInput,
            currencyCode = model.secondaryCurrencyCode,
            onValueChange = onSecondaryAmountChanged,
            trailingIcon = Icons.Rounded.UnfoldMore,
            onTrailingClick = onCycleSignOrMode,
            onCalcClick = onOpenSecondaryCalculator,
        )
    }
}

@Composable
private fun DualAmountLine(
    dotColor: Color,
    header: String,
    accountLabel: String,
    sign: String,
    signColor: Color,
    input: String,
    currencyCode: String,
    onValueChange: (String) -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onTrailingClick: () -> Unit,
    onCalcClick: () -> Unit,
) {
    Column {
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
                        .background(dotColor)
                )
                Text(
                    text = header,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchColors.TextSecondary,
                )
            }
            Text(text = accountLabel, fontSize = 11.sp, color = StitchColors.TextTertiary)
        }
        Spacer(modifier = Modifier.height(4.dp))
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = signColor,
                    modifier = Modifier.padding(end = 6.dp),
                )
                val inputState = rememberSyncedTextFieldState(input, onValueChange)
                BasicTextField(
                    state = inputState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(StitchColors.Primary),
                    textStyle = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StitchColors.TextPrimary,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = currencyCode,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchColors.TextSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onTrailingClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = StitchColors.TextSecondary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(StitchDimens.RadiusTile),
                    color = StitchColors.SurfaceWhite,
                    border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.CardBorder),
                ) {
                    IconButton(onClick = onCalcClick, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = "Open calculator",
                            tint = StitchColors.Primary,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Hero Amount Card - Single Currency", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun HeroAmountCardSingleCurrencyPreview() {
    TransactionStitchTheme {
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

@Preview(name = "Hero Amount Card - Dual Currency", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun HeroAmountCardDualCurrencyPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            HeroAmountCard(
                model = HeroAmountDisplayModel(
                    mode = TransactionMode.TRANSFER,
                    isDualCurrency = true,
                    primaryInput = "100.00",
                    primaryCurrencyCode = "USD",
                    primaryCurrencySymbol = "$",
                    primaryAccountLabel = "Main Checking",
                    secondaryInput = "92.50",
                    secondaryCurrencyCode = "EUR",
                    secondaryCurrencySymbol = "€",
                    secondaryAccountLabel = "Travel Card",
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

