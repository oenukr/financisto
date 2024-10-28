package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.os.AsyncTask
import android.view.View
import android.widget.TextView

import androidx.core.content.ContextCompat

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.IntegrityCheck
import java.lang.ref.WeakReference

class IntegrityCheckTask(
    activity: Activity,
) : AsyncTask<IntegrityCheck, Void, IntegrityCheck.Result>() {

    private val activity: WeakReference<Activity> = WeakReference(activity)

    override fun doInBackground(vararg params: IntegrityCheck): IntegrityCheck.Result {
        val textView = getResultView()
        val context = activity.get()?.baseContext
        if (textView != null && context != null) {
            return params[0].check(context)
        }
        return IntegrityCheck.Result.OK
    }

    override fun onPostExecute(result: IntegrityCheck.Result?) {
        val textView = getResultView()
        if (textView != null) {
            if (result?.level == IntegrityCheck.Level.OK) {
                textView.visibility = View.GONE
            } else {
                textView.visibility = View.VISIBLE
                activity.get()?.let { context ->
                    textView.setBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            colorForLevel(result?.level)
                        )
                    )
                    textView.text =
                        context.getString(R.string.integrity_error_message, result?.message)
                }
            }
        }
    }

    private fun colorForLevel(level: IntegrityCheck.Level?): Int {
        return when (level) {
            IntegrityCheck.Level.INFO -> R.color.holo_green_dark
            IntegrityCheck.Level.WARN -> R.color.holo_orange_dark
            else -> R.color.holo_red_dark
        }
    }

    private fun getResultView(): TextView? = activity.get()?.findViewById(R.id.integrity_error)
}
