// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption

private const val ConduitLineAlpha = 0.5f
private const val ConduitStrokeWidthPx = 3f
private const val DashOnPx = 8f
private const val DashOffPx = 8f

@Composable
fun SingleAccountAndCurrencyPills(
    account: AccountOption?,
    selectedCurrency: CurrencyOption?,
    showCurrencySelector: Boolean,
    onAccountClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactSelectorPill(
            icon = Icons.Rounded.AccountBalanceWallet,
            prefix = stringResource(R.string.tx_label_account_prefix),
            value = account?.title ?: stringResource(R.string.tx_placeholder_select_account),
            onClick = onAccountClick,
        )
        if (showCurrencySelector) {
            val currencyLabel = if (selectedCurrency != null && selectedCurrency.id != account?.currencyId) {
                selectedCurrency.code
            } else {
                val code = account?.currencyCode.orEmpty()
                if (code.isNotEmpty()) {
                    stringResource(R.string.tx_currency_same_as_account_code, code)
                } else {
                    stringResource(R.string.tx_currency_same_as_account)
                }
            }
            CompactSelectorPill(
                icon = Icons.Rounded.Payments,
                prefix = stringResource(R.string.tx_label_currency_prefix),
                value = currencyLabel,
                onClick = onCurrencyClick,
            )
        }
    }
}

@Composable
fun TransferAccountsGroupCard(
    fromAccount: AccountOption?,
    toAccount: AccountOption?,
    outAmountMinor: Long,
    inAmountMinor: Long,
    onFromAccountClick: () -> Unit,
    onToAccountClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.RadiusHeroCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(dimens.DefaultBorderWidth, semantic.heroBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TransferAccountRow(
                label = stringResource(R.string.tx_label_from_account),
                account = fromAccount,
                deltaMinor = if (outAmountMinor > 0L) -outAmountMinor else null,
                isDestination = false,
                onClick = onFromAccountClick,
            )
            SwapConduitRow(onSwapClick = onSwapClick)
            TransferAccountRow(
                label = stringResource(R.string.tx_label_to_account),
                account = toAccount,
                deltaMinor = if (inAmountMinor > 0L) inAmountMinor else null,
                isDestination = true,
                onClick = onToAccountClick,
            )
        }
    }
}

@Composable
private fun CompactSelectorPill(
    icon: ImageVector,
    prefix: String,
    value: String,
    onClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.RadiusPill),
        color = semantic.pillSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(prefix)
                            append(' ')
                        }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(value)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TransferAccountRow(
    label: String,
    account: AccountOption?,
    deltaMinor: Long?,
    isDestination: Boolean,
    onClick: () -> Unit,
) {
    val dimens = FinancistoTheme.dimens
    val tileBg = if (isDestination) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val tileTint = if (isDestination) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = if (isDestination) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalanceWallet

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.RadiusPill))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .background(color = tileBg, shape = RoundedCornerShape(dimens.RadiusInnerCard)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tileTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            TransferAccountLabels(label = label, account = account)
        }
        TransferAccountTrailing(account = account, deltaMinor = deltaMinor)
    }
}

@Composable
private fun TransferAccountLabels(
    label: String,
    account: AccountOption?,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = account?.title ?: stringResource(R.string.tx_placeholder_select_account),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (account != null) {
            val balText = AmountScale.formatDisplayAmount(
                minorUnits = account.balanceMinor,
                decimals = account.currencyDecimals,
                symbol = account.currencySymbol.ifBlank { account.currencyCode },
                includeSign = account.balanceMinor < 0L,
            )
            Text(
                text = stringResource(R.string.tx_account_meta_format, account.typeLabel, balText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferAccountTrailing(
    account: AccountOption?,
    deltaMinor: Long?,
) {
    val semantic = FinancistoTheme.semanticColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (deltaMinor != null && account != null) {
            val isPos = deltaMinor > 0L
            val chipBg = if (isPos) semantic.incomeContainer else semantic.debitContainer
            val chipFg = if (isPos) semantic.onIncomeContainer else semantic.onDebitContainer
            Text(
                text = AmountScale.formatDisplayAmount(
                    minorUnits = deltaMinor,
                    decimals = account.currencyDecimals,
                    symbol = account.currencySymbol.ifBlank { account.currencyCode },
                    includeSign = true,
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = chipFg,
                modifier = Modifier
                    .background(color = chipBg, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SwapConduitRow(onSwapClick: () -> Unit) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = ConduitLineAlpha)
        Canvas(
            modifier = Modifier
                .width(2.dp)
                .height(48.dp),
        ) {
            drawLine(
                color = lineColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = ConduitStrokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOnPx, DashOffPx), 0f),
            )
        }
        Surface(
            onClick = onSwapClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(dimens.DefaultBorderWidth, semantic.heroBorder),
            shadowElevation = 2.dp,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = stringResource(R.string.tx_cd_swap_accounts),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(name = "Single Account & Currency Pills - Light", showBackground = true)
@Composable
private fun SingleAccountAndCurrencyPillsPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SingleAccountAndCurrencyPills(
                    account = AccountOption(
                        id = 1L,
                        title = "Main Checking",
                        typeLabel = "Bank",
                        currencyId = 1L,
                        currencyCode = "USD",
                        currencySymbol = "$",
                        currencyDecimals = 2,
                        balanceMinor = 245075L,
                    ),
                    selectedCurrency = CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
                    showCurrencySelector = true,
                    onAccountClick = {},
                    onCurrencyClick = {},
                )
            }
        }
    }
}

@Preview(name = "Transfer Accounts Group Card - Dark", showBackground = true)
@Composable
private fun TransferAccountsGroupCardDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                TransferAccountsGroupCard(
                    fromAccount = AccountOption(
                        id = 1L,
                        title = "Main Checking",
                        typeLabel = "Bank",
                        currencyId = 1L,
                        currencyCode = "USD",
                        currencySymbol = "$",
                        currencyDecimals = 2,
                        balanceMinor = 245075L,
                    ),
                    toAccount = AccountOption(
                        id = 2L,
                        title = "Travel Card",
                        typeLabel = "Credit Card",
                        currencyId = 2L,
                        currencyCode = "EUR",
                        currencySymbol = "€",
                        currencyDecimals = 2,
                        balanceMinor = 84050L,
                    ),
                    outAmountMinor = 15000L,
                    inAmountMinor = 13850L,
                    onFromAccountClick = {},
                    onToAccountClick = {},
                    onSwapClick = {},
                )
            }
        }
    }
}
