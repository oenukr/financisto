package ru.orangesoftware.financisto.activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.MultiChoiceItem
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.view.NodeInflater

abstract class AbstractActivity : FragmentActivity(), ActivityLayoutListener {

    protected lateinit var db: DatabaseAdapter
    protected lateinit var activityLayout: ActivityLayout

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nodeInflater = NodeInflater(LayoutInflater.from(this))
        activityLayout = ActivityLayout(nodeInflater, this)
        db = DatabaseAdapter(this)
        db.open()
    }

    override fun onPause() {
        super.onPause()
        if (shouldLock()) {
            PinProtection.lock(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldLock()) {
            PinProtection.unlock(this)
        }
    }

    protected open fun shouldLock(): Boolean {
        return true
    }

    override fun onClick(v: View) {
        val id = v.id
        onClick(v, id)
    }

    protected abstract fun onClick(v: View, id: Int)

    override fun onSelected(id: Int, items: List<MultiChoiceItem>) {}

    override fun onSelectedId(id: Int, selectedId: Long) {}

    override fun onSelectedPos(id: Int, selectedPos: Int) {}

    protected fun checkSelected(value: Any?, @StringRes messageResId: Int): Boolean {
        if (value == null) {
            Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    protected fun checkSelectedId(value: Long, @StringRes messageResId: Int): Boolean {
        if (value <= 0) {
            Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    companion object {
        @JvmStatic
        fun setVisibility(v: View?, visibility: Int) {
            v?.visibility = visibility
            val tag = v?.tag
            if (tag is View) {
                tag.visibility = visibility
            }
        }
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
