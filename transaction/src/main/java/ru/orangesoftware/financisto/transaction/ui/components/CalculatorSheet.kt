// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.CalculatorExpressionEvaluator
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import java.math.BigDecimal
import java.math.RoundingMode

private const val CalcGridColumns = 4
private const val CalcMaxFractionDigits = 8
private val CalcOperatorKeys = setOf("C", "÷", "×", "−", "+", "=")
private val CalcKeypadRows = listOf(
    listOf("C", "(", ")", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf(".", "0", "⌫", "="),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorBottomSheet(
    target: CalculatorTarget,
    initialText: String,
    onDismiss: () -> Unit,
    onApplyValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialExpression = if (initialText == "0" || initialText == "0.00") "" else initialText
    val expressionState = rememberTextFieldState(initialText = initialExpression)
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialText) {
        val normalized = if (initialText == "0" || initialText == "0.00") "" else initialText
        if (expressionState.text.toString() != normalized) {
            expressionState.setTextAndPlaceCursorAtEnd(normalized)
        }
    }
    LaunchedEffect(expressionState) {
        snapshotFlow { expressionState.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .collect { errorMsg = null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        CalculatorBottomSheetContent(
            target = target,
            expressionState = expressionState,
            errorMsg = errorMsg,
            onError = { errorMsg = it },
            onClearError = { errorMsg = null },
            onApplyValue = onApplyValue,
        )
    }
}

@Composable
internal fun CalculatorBottomSheetContent(
    target: CalculatorTarget,
    expressionState: TextFieldState,
    errorMsg: String?,
    onError: (String) -> Unit,
    onClearError: () -> Unit,
    onApplyValue: (Double) -> Unit,
) {
    val invalidMessage = stringResource(R.string.tx_error_invalid_expression)
    val titleRes = when (target) {
        CalculatorTarget.EXCHANGE_RATE -> R.string.tx_sheet_rate_calculator
        else -> R.string.tx_sheet_amount_calculator
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            state = expressionState,
            readOnly = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text("0") },
            isError = errorMsg?.isNotEmpty() == true,
            supportingText = errorMsg?.let { msg -> { Text(msg) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        CalculatorKeypadGrid(
            onKeyClick = { key ->
                onClearError()
                val updated = handleCalculatorKey(
                    current = expressionState.text.toString(),
                    key = key,
                    onInvalidExpression = { onError(invalidMessage) },
                )
                expressionState.setTextAndPlaceCursorAtEnd(updated)
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        CalculatorApplyButton(
            onClick = {
                val value = CalculatorExpressionEvaluator.evaluate(
                    expressionState.text.toString().ifBlank { "0" },
                )
                if (value != null) onApplyValue(value) else onError(invalidMessage)
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CalculatorApplyButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(
            text = stringResource(R.string.tx_action_apply),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CalculatorKeypadGrid(onKeyClick: (String) -> Unit) {
    Grid(
        config = {
            repeat(CalcGridColumns) { column(1.fr) }
            repeat(CalcKeypadRows.size) { row(48.dp) }
            gap(6.dp)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        CalcKeypadRows.forEachIndexed { rowIdx, rowKeys ->
            rowKeys.forEachIndexed { colIdx, key ->
                CalculatorKeyButton(
                    key = key,
                    onClick = { onKeyClick(key) },
                    modifier = Modifier
                        .gridItem(row = rowIdx + 1, column = colIdx + 1)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CalculatorKeyButton(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    val isAction = key in CalcOperatorKeys
    val keyBgColor = when {
        key == "=" -> MaterialTheme.colorScheme.primary
        isAction -> semantic.topChipBackground
        else -> semantic.pillSurface
    }
    val keyContentColor = if (key == "=") {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val accessibleLabel = calculatorKeyDescription(key)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile),
        color = keyBgColor,
        modifier = if (accessibleLabel != null) {
            modifier.semantics { contentDescription = accessibleLabel }
        } else {
            modifier
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = keyContentColor,
            )
        }
    }
}

@Composable
private fun calculatorKeyDescription(key: String): String? {
    val resId = when (key) {
        "C" -> R.string.tx_cd_calc_clear
        "⌫" -> R.string.tx_cd_calc_backspace
        "÷" -> R.string.tx_cd_calc_divide
        "×" -> R.string.tx_cd_calc_multiply
        "−" -> R.string.tx_cd_calc_subtract
        "+" -> R.string.tx_cd_calc_add
        "=" -> R.string.tx_cd_calc_equals
        else -> null
    }
    return resId?.let { stringResource(it) }
}

private fun handleCalculatorKey(
    current: String,
    key: String,
    onInvalidExpression: () -> Unit,
): String {
    return when (key) {
        "C" -> ""
        "⌫" -> if (current.isNotEmpty()) current.dropLast(1) else ""
        "=" -> {
            val value = CalculatorExpressionEvaluator.evaluate(current.ifBlank { "0" })
            if (value != null) {
                formatCalculatorResult(value)
            } else {
                onInvalidExpression()
                current
            }
        }
        "×" -> "$current*"
        "÷" -> "$current/"
        "−" -> "$current-"
        else -> "$current$key"
    }
}

private fun formatCalculatorResult(value: Double): String {
    val normalized = BigDecimal.valueOf(value)
        .setScale(CalcMaxFractionDigits, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return if (normalized.compareTo(BigDecimal.ZERO) == 0) {
        "0"
    } else {
        normalized.toPlainString()
    }
}

@Preview(name = "Calculator Bottom Sheet - Light", showBackground = true)
@Composable
private fun CalculatorBottomSheetPreview() {
    FinancistoTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CalculatorBottomSheetContent(
                target = CalculatorTarget.PRIMARY_AMOUNT,
                expressionState = rememberTextFieldState(initialText = "125+40"),
                errorMsg = null,
                onError = {},
                onClearError = {},
                onApplyValue = {},
            )
        }
    }
}

@Preview(name = "Calculator Bottom Sheet - Dark", showBackground = true)
@Composable
private fun CalculatorBottomSheetDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CalculatorBottomSheetContent(
                target = CalculatorTarget.EXCHANGE_RATE,
                expressionState = rememberTextFieldState(initialText = "100/92.5"),
                errorMsg = null,
                onError = {},
                onClearError = {},
                onApplyValue = {},
            )
        }
    }
}
