package ru.orangesoftware.financisto.widget

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher

/**
 * https://stackoverflow.com/a/35268540/365675
 */
abstract class SearchFilterTextWatcherListener(private val delayMs: Long) : TextWatcher {
    private val handler: Handler = Handler(Looper.getMainLooper() /*UI thread*/)
    private lateinit var workRunnable: Runnable

    abstract fun clearFilter(oldFilter: String)
    abstract fun applyFilter(filter: String)

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        handler.removeCallbacks(workRunnable)
        clearFilter(s.toString())
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // ignore
    }

    override fun afterTextChanged(s: Editable) {
        workRunnable = Runnable {
//                    Toast.makeText(SelectTemplateActivity.this, "Filtering...", Toast.LENGTH_SHORT).show();
            applyFilter(s.toString())
        }
        handler.postDelayed(workRunnable, delayMs)
    }
}
