package ru.orangesoftware.financisto.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.preference.DialogPreference
import android.util.AttributeSet

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.view.PinView

class PinDialogPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyle: Int = 0,
) : DialogPreference(context, attrs, defStyle), PinView.PinListener {

	private var dialog: Dialog? = null

    @Deprecated("Deprecated in Java")
	override fun showDialog(state: Bundle) {
		val context: Context = context
		val pinView = PinView(context, this, R.layout.lock)
		dialog = AlertDialog.Builder(context)
        	.setTitle(R.string.set_pin)
        	.setView(pinView.view)
        	.create()
		dialog?.setOnDismissListener(this)
		dialog?.show()
    }

	override fun onConfirm(pinBase64: String) {
		dialog?.setTitle(R.string.confirm_pin)
	}
		
	override fun onSuccess(pinBase64: String) {
		persistString(pinBase64)
		dialog?.dismiss()
	}
}
