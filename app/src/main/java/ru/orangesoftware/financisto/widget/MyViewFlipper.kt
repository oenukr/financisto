package ru.orangesoftware.financisto.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.ViewFlipper

class MyViewFlipper @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet,
	defStyleAttr: Int = 0
) : ViewFlipper(context, attrs) {

	override fun onDetachedFromWindow() {
		try {
			super.onDetachedFromWindow()
		} catch (e: IllegalArgumentException) {
			Log.w("MyViewFlipper", "Android project issue 6191 workaround.")
		} finally {
			super.stopFlipping()
		}
	}

}
