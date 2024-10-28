package ru.orangesoftware.financisto.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.RelativeLayout

class TransparentLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : RelativeLayout(context, attrs) {

	private var innerPaint: Paint = Paint().apply {
		setARGB(225, 75, 75, 75) //gray
		isAntiAlias = true
	}
	private var borderPaint: Paint = Paint().apply {
		setARGB(255, 255, 255, 255)
		isAntiAlias = true
		style = Style.STROKE
		strokeWidth = 2F
	}
    
	override fun dispatchDraw(canvas: Canvas) {
		val drawRect = RectF()
		drawRect.set(
			/* left = */ 0F,
			/* top = */ 0F,
			/* right = */ measuredWidth.toFloat(),
			/* bottom = */ measuredHeight.toFloat(),
		)

		canvas.drawRoundRect(drawRect, 5F, 5F, innerPaint)
		canvas.drawRoundRect(drawRect, 5F, 5F, borderPaint)

		super.dispatchDraw(canvas)
	}
}
