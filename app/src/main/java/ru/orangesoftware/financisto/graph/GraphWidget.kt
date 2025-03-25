package ru.orangesoftware.financisto.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.orangesoftware.financisto.R
import kotlin.math.abs

@Composable
fun GraphComposable(
    modifier: Modifier = Modifier,
    unit: GraphUnit,
    maxAmount: Long,
    maxAmountWidth: Long,
    onItemClick: (id: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val width by remember { mutableIntStateOf(context.resources.displayMetrics.widthPixels) }

    val positiveColor by remember {
        mutableStateOf(
            Color(
                ContextCompat.getColor(
                    context,
                    R.color.positive_amount
                )
            )
        )
    }
    val negativeColor by remember {
        mutableStateOf(
            Color(
                ContextCompat.getColor(
                    context,
                    R.color.negative_amount
                )
            )
        )
    }
    val positiveLineColor: Color by remember { mutableStateOf(Color(124, 198, 35, 255)) }
    val negativeLineColor: Color by remember { mutableStateOf(Color(239, 156, 0, 255)) }

    val zeroColor: Color = colorResource(android.R.color.secondary_text_dark)
    val style = unit.style

    val contentPadding = modifier.padding(
        horizontal = style.indent.dp,
        vertical = PaddingValues(top = 0.dp).calculateTopPadding(),
    )

    Column(modifier = contentPadding.clickable { onItemClick(unit.id) }) {
        val annotatedName = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(style.namePaint.color))) {
                append(unit.name)
            }
        }
        Text(text = annotatedName)
        unit.forEach { a ->
            val amount = a.amount

            val color: Color = when {
                amount > 0 -> positiveLineColor
                amount < 0 -> negativeLineColor
                else -> zeroColor
            }

            val textColor: Color = when {
                amount > 0 -> positiveColor
                amount < 0 -> negativeColor
                else -> zeroColor
            }

            Box {
                val lineWidth = with(density) {
                    maxOf(
                        1f,
                        (abs(amount.toFloat()) / maxAmount * (width - (style.textDy * 5) - maxAmountWidth)).coerceAtLeast(
                            1f
                        )
                    ).toDp()
                }
                val lineHeight = with(density) { style.lineHeight.toDp() }
                val textDy = with(density) { style.textDy.toDp() }
                val textSize = with(density) { style.amountPaint.textSize.toSp() }

                Box(Modifier
					.height(lineHeight)
					.width(lineWidth)
					.background(color = color))
                val annotatedAmount = buildAnnotatedString {
                    withStyle(SpanStyle(color = textColor)) {
                        append(a.getAmountText())
                    }
                }
                Text(
                    text = annotatedAmount,
                    modifier = Modifier
                        .offset(x = lineWidth + textDy),
                    fontSize = textSize,
                )
            }
        }
    }
}
