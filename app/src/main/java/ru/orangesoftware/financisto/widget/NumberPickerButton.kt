package ru.orangesoftware.financisto.widget

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent

import ru.orangesoftware.financisto.R

/**
 * This class exists purely to cancel long click events.
 */
class NumberPickerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : androidx.appcompat.widget.AppCompatImageButton(
    context,
    attrs,
    defStyle,
) {
    lateinit var numberPicker: NumberPicker
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        cancelLongpressIfRequired(event)
        return super.onTouchEvent(event)
    }
    
    override fun onTrackballEvent(event: MotionEvent): Boolean {
        cancelLongpressIfRequired(event)
        return super.onTrackballEvent(event)
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                || (keyCode == KeyEvent.KEYCODE_ENTER)) {
            cancelLongpress()
        }
        return super.onKeyUp(keyCode, event)
    }
    
    private fun cancelLongpressIfRequired(event: MotionEvent) {
        if ((event.action == MotionEvent.ACTION_CANCEL)
                || (event.action == MotionEvent.ACTION_UP)) {
            cancelLongpress()
        }
    }

    private fun cancelLongpress() {
        if (R.id.increment == id) {
            numberPicker.cancelIncrement()
        } else if (R.id.decrement == id) {
            numberPicker.cancelDecrement()
        }
    }
}
