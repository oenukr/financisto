package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.utils.IntegrityCheck
import java.lang.ref.WeakReference

class IntegrityCheckTask(
    activity: Activity,
) {
    private val activityRef: WeakReference<Activity> = WeakReference(activity)

    fun execute(vararg params: IntegrityCheck): IntegrityCheckTask {
        if (params.isEmpty()) return this
        val check = params[0]
        val act = activityRef.get() ?: return this
        val scope = (act as? LifecycleOwner)?.lifecycleScope ?: DependenciesHolder().applicationScope

        scope.launch {
            val startActivity = activityRef.get() ?: return@launch
            if (startActivity.findViewById<TextView?>(R.id.integrity_error) == null) return@launch
            val context = startActivity.applicationContext
            val result = withContext(Dispatchers.IO) { check.check(context) }

            val currentActivity = activityRef.get() ?: return@launch
            val textView = currentActivity.findViewById<TextView?>(R.id.integrity_error) ?: return@launch

            if (result.level == IntegrityCheck.Level.OK) {
                textView.visibility = View.GONE
            } else {
                textView.visibility = View.VISIBLE
                textView.setBackgroundColor(
                    ContextCompat.getColor(
                        currentActivity,
                        colorForLevel(result.level),
                    ),
                )
                textView.text = currentActivity.getString(R.string.integrity_error_message, result.message)
            }
        }
        return this
    }

    private fun colorForLevel(level: IntegrityCheck.Level?): Int {
        return when (level) {
            IntegrityCheck.Level.INFO -> R.color.holo_green_dark
            IntegrityCheck.Level.WARN -> R.color.holo_orange_dark
            else -> R.color.holo_red_dark
        }
    }
}
