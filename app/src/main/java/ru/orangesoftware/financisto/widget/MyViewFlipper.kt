package ru.orangesoftware.financisto.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ViewFlipper
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.utils.Logger

class MyViewFlipper @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet,
	defStyleAttr: Int = 0,
) : ViewFlipper(context, attrs) {

	private val logger: Logger = DependenciesHolder().logger

	override fun onDetachedFromWindow() {
		try {
			super.onDetachedFromWindow()
		} catch (e: IllegalArgumentException) {
			logger.w("Android project issue 6191 workaround.")
		} finally {
			super.stopFlipping()
		}
	}

}
