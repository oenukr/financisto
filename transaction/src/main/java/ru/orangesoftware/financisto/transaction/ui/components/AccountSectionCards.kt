// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

@Composable
fun SingleAccountAndCurrencyPills(
    account: AccountOption?,
    selectedCurrency: CurrencyOption?,
    showCurrencySelector: Boolean,
    onAccountClick: () -> Unit,
    onCurrencyClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactSelectorPill(
            icon = Icons.Rounded.AccountBalanceWallet,
            prefix = "Account: ",
            value = account?.title ?: "Select account",
            onClick = onAccountClick,
        )
        if (showCurrencySelector) {
            val currencyLabel = if (selectedCurrency != null && selectedCurrency.id != account?.currencyId) {
                selectedCurrency.code
            } else {
                val code = account?.currencyCode.orEmpty()
                if (code.isNotEmpty()) "Same as account ($code)" else "Same as account"
            }
            CompactSelectorPill(
                icon = Icons.Rounded.Payments,
                prefix = "Currency: ",
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StitchDimens.RadiusHeroCard),
        color = StitchColors.SurfaceWhite,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TransferAccountRow(
                label = "FROM ACCOUNT",
                account = fromAccount,
                deltaMinor = if (outAmountMinor > 0L) -outAmountMinor else null,
                isDestination = false,
                onClick = onFromAccountClick,
            )
            SwapConduitRow(onSwapClick = onSwapClick)
            TransferAccountRow(
                label = "TO ACCOUNT",
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StitchDimens.RadiusPill))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(StitchDimens.RadiusPill),
        color = StitchColors.PillSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
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
                    tint = StitchColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = StitchColors.TextSecondary)) { append(prefix) }
                        withStyle(SpanStyle(color = StitchColors.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                            append(value)
                        }
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = StitchColors.TextSecondary,
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
    val tileBg = if (isDestination) StitchColors.Primary else StitchColors.PrimaryContainer
    val tileTint = if (isDestination) Color.White else StitchColors.Primary
    val icon = if (isDestination) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalanceWallet

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StitchDimens.RadiusPill))
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
            Surface(
                shape = RoundedCornerShape(StitchDimens.RadiusInnerCard),
                color = tileBg,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tileTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = StitchColors.TextTertiary,
                )
                Text(
                    text = account?.title ?: "Select account",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = StitchColors.TextPrimary,
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
                        text = "${account.typeLabel} • $balText",
                        fontSize = 11.sp,
                        color = StitchColors.TextSecondary,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (deltaMinor != null && account != null) {
                val isPos = deltaMinor > 0L
                val chipBg = if (isPos) StitchColors.IncomeBadgeBg else StitchColors.DebitChipBg
                val chipFg = if (isPos) StitchColors.IncomeBadgeText else StitchColors.DebitText
                Surface(shape = RoundedCornerShape(6.dp), color = chipBg) {
                    Text(
                        text = AmountScale.formatDisplayAmount(
                            minorUnits = deltaMinor,
                            decimals = account.currencyDecimals,
                            symbol = account.currencySymbol.ifBlank { account.currencyCode },
                            includeSign = true,
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = chipFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = StitchColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun SwapConduitRow(onSwapClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        val lineColor = StitchColors.TextTertiary.copy(alpha = 0.5f)
        Canvas(
            modifier = Modifier
                .width(2.dp)
                .height(40.dp)
        ) {
            drawLine(
                color = lineColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
        }
        Surface(
            shape = CircleShape,
            color = StitchColors.SurfaceWhite,
            border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onSwapClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Swap accounts",
                    tint = StitchColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Preview(name = "Single Account & Currency Pills", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SingleAccountAndCurrencyPillsPreview() {
    TransactionStitchTheme {
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
                selectedCurrency = CurrencyOption(
                    id = 2L,
                    code = "EUR",
                    symbol = "€",
                    decimals = 2,
                ),
                showCurrencySelector = true,
                onAccountClick = {},
                onCurrencyClick = {},
            )
        }
    }
}

@Preview(name = "Single Account Pills (Empty)", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SingleAccountAndCurrencyPillsEmptyPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SingleAccountAndCurrencyPills(
                account = null,
                selectedCurrency = null,
                showCurrencySelector = true,
                onAccountClick = {},
                onCurrencyClick = {},
            )
        }
    }
}

@Preview(name = "Transfer Accounts Group Card", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun TransferAccountsGroupCardPreview() {
    TransactionStitchTheme {
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
