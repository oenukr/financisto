// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.CurrencyConversionCalculator

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
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.RadiusPill),
        color = semantic.heroSurface,
        border = BorderStroke(dimens.DefaultBorderWidth, semantic.heroBorder),
    ) {
        Column(modifier = Modifier.padding(dimens.CardPadding)) {
            ExchangeRateHeaderRow(
                isFetchingRate = isFetchingRate,
                onSyncRateClick = onSyncRateClick,
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                    Text(
                        text = fwd,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = inv,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateHeaderRow(
    isFetchingRate: Boolean,
    onSyncRateClick: () -> Unit,
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
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.tx_label_conversion_rate),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onSyncRateClick,
            enabled = !isFetchingRate,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (isFetchingRate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(
                    if (isFetchingRate) R.string.tx_action_syncing else R.string.tx_action_sync_rate,
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val boxShape = RoundedCornerShape(dimens.RadiusTile)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface, shape = boxShape)
            .border(width = dimens.DefaultBorderWidth, color = semantic.cardBorder, shape = boxShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tx_label_effective_rate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rateState = rememberSyncedTextFieldState(rateInput, onRateInputChange)
                BasicTextField(
                    state = rateState,
                    inputTransformation = DecimalInputTransformation,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(R.string.tx_rate_pair_format, toCode, fromCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        RateInputActionButtons(
            isFetchingRate = isFetchingRate,
            onSyncRateClick = onSyncRateClick,
            onOpenRateCalculator = onOpenRateCalculator,
        )
    }
}

@Composable
private fun RateInputActionButtons(
    isFetchingRate: Boolean,
    onSyncRateClick: () -> Unit,
    onOpenRateCalculator: () -> Unit,
) {
    val iconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = FinancistoTheme.semanticColors.pillSurface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(
            onClick = onSyncRateClick,
            enabled = !isFetchingRate,
            shape = RoundedCornerShape(8.dp),
            colors = iconButtonColors,
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.tx_cd_download_rate),
                modifier = Modifier.size(18.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onOpenRateCalculator,
            shape = RoundedCornerShape(8.dp),
            colors = iconButtonColors,
        ) {
            Icon(
                imageVector = Icons.Rounded.Calculate,
                contentDescription = stringResource(R.string.tx_cd_rate_calculator),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(name = "Exchange Rate Card - Light", showBackground = true)
@Composable
private fun ExchangeRateCardPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
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
}

@Preview(name = "Exchange Rate Card - Dark Fetching", showBackground = true)
@Composable
private fun ExchangeRateCardFetchingDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
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
}
