// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.domain.CurrencyConversionCalculator
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

@Composable
fun ExchangeRateCard(
    rateInput: String,
    rateValue: Double,
    fromCode: String,
    toCode: String,
    isFetchingRate: Boolean,
    onRateInputChange: (String) -> Unit,
    onSyncRateClick: () -> Unit,
    onOpenRateCalculator: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StitchDimens.RadiusPill),
        color = StitchColors.HeroSurface,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
    ) {
        Column(modifier = Modifier.padding(StitchDimens.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = StitchColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "CONVERSION RATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = StitchColors.TextSecondary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isFetchingRate, onClick = onSyncRateClick)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    if (isFetchingRate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = StitchColors.Primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = StitchColors.Primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = if (isFetchingRate) "Syncing..." else "Sync Rate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchColors.Primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            RateInputBox(
                rateInput = rateInput,
                fromCode = fromCode,
                toCode = toCode,
                isFetchingRate = isFetchingRate,
                onRateInputChange = onRateInputChange,
                onSyncRateClick = onSyncRateClick,
                onOpenRateCalculator = onOpenRateCalculator,
            )
            CurrencyConversionCalculator.formatReciprocalPair(fromCode, toCode, rateValue)?.let { (fwd, inv) ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = fwd, fontSize = 11.sp, color = StitchColors.TextTertiary)
                    Text(text = inv, fontSize = 11.sp, color = StitchColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun RateInputBox(
    rateInput: String,
    fromCode: String,
    toCode: String,
    isFetchingRate: Boolean,
    onRateInputChange: (String) -> Unit,
    onSyncRateClick: () -> Unit,
    onOpenRateCalculator: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = StitchColors.SurfaceWhite,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.CardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Effective Rate applied",
                    fontSize = 11.sp,
                    color = StitchColors.TextTertiary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rateState = rememberSyncedTextFieldState(rateInput, onRateInputChange)
                    BasicTextField(
                        state = rateState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(StitchColors.Primary),
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = StitchColors.TextPrimary,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "$toCode / $fromCode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StitchColors.TextSecondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StitchColors.PillSurface,
                ) {
                    IconButton(
                        onClick = onSyncRateClick,
                        enabled = !isFetchingRate,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download rate",
                            tint = StitchColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StitchColors.PillSurface,
                ) {
                    IconButton(
                        onClick = onOpenRateCalculator,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = "Rate calculator",
                            tint = StitchColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Exchange Rate Card", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun ExchangeRateCardPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ExchangeRateCard(
                rateInput = "0.92",
                rateValue = 0.92,
                fromCode = "USD",
                toCode = "EUR",
                isFetchingRate = false,
                onRateInputChange = {},
                onSyncRateClick = {},
                onOpenRateCalculator = {},
            )
        }
    }
}

@Preview(name = "Exchange Rate Card (Fetching)", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun ExchangeRateCardFetchingPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ExchangeRateCard(
                rateInput = "0.92",
                rateValue = 0.92,
                fromCode = "USD",
                toCode = "EUR",
                isFetchingRate = true,
                onRateInputChange = {},
                onSyncRateClick = {},
                onOpenRateCalculator = {},
            )
        }
    }
}
