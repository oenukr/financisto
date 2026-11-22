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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import ru.orangesoftware.financisto.transaction.domain.CalculatorExpressionEvaluator
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

private const val CALC_GRID_COLUMNS = 4
private val CALC_KEYPAD_ROWS = listOf(
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
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
private fun CalculatorBottomSheetContent(
    target: CalculatorTarget,
    expressionState: TextFieldState,
    errorMsg: String?,
    onError: (String) -> Unit,
    onClearError: () -> Unit,
    onApplyValue: (Double) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val title = when (target) {
            CalculatorTarget.EXCHANGE_RATE -> "Rate Calculator"
            else -> "Amount Calculator"
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StitchColors.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            state = expressionState,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text("0") },
            isError = errorMsg?.isNotEmpty() == true,
            supportingText = errorMsg?.let { msg -> { Text(msg) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Grid(
            config = {
                repeat(CALC_GRID_COLUMNS) { column(1.fr) }
                repeat(CALC_KEYPAD_ROWS.size) { row(46.dp) }
                gap(6.dp)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            CALC_KEYPAD_ROWS.forEachIndexed { rowIdx, rowKeys ->
                rowKeys.forEachIndexed { colIdx, key ->
                    CalculatorKeyButton(
                        key = key,
                        onClick = {
                            onClearError()
                            val updated = handleCalculatorKey(
                                current = expressionState.text.toString(),
                                key = key,
                            ) { evaluated ->
                                if (evaluated != null) {
                                    onApplyValue(evaluated)
                                } else {
                                    onError("Invalid expression")
                                }
                            }
                            expressionState.setTextAndPlaceCursorAtEnd(updated)
                        },
                        modifier = Modifier
                            .gridItem(row = rowIdx + 1, column = colIdx + 1)
                            .fillMaxSize(),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val value = CalculatorExpressionEvaluator.evaluate(
                    expressionState.text.toString().ifBlank { "0" }
                )
                if (value != null) onApplyValue(value) else onError("Invalid expression")
            },
            colors = ButtonDefaults.buttonColors(containerColor = StitchColors.Primary, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Apply", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CalculatorKeyButton(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAction = key in setOf("C", "÷", "×", "−", "+", "=")
    val keyBgColor = when {
        key == "=" -> StitchColors.Primary
        isAction -> StitchColors.TopChipBg
        else -> StitchColors.PillSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = keyBgColor,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (key == "=") Color.White else StitchColors.TextPrimary,
            )
        }
    }
}

private fun handleCalculatorKey(
    current: String,
    key: String,
    onEvaluated: (Double?) -> Unit,
): String {
    return when (key) {
        "C" -> ""
        "⌫" -> if (current.isNotEmpty()) current.dropLast(1) else ""
        "=" -> {
            val normalized = current.replace('×', '*').replace('÷', '/').replace('−', '-')
            val value = CalculatorExpressionEvaluator.evaluate(normalized.ifBlank { "0" })
            onEvaluated(value)
            value?.toString() ?: current
        }
        "×" -> "$current*"
        "÷" -> "$current/"
        "−" -> "$current-"
        else -> "$current$key"
    }
}

@Preview(name = "Calculator Bottom Sheet", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun CalculatorBottomSheetPreview() {
    TransactionStitchTheme {
        Surface(
            color = StitchColors.SurfaceWhite,
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
