package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.Window
import android.widget.CursorAdapter
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import java.util.LinkedList

@Suppress("TooManyFunctions")
abstract class AbstractListActivity protected constructor(
    private val contentId: Int,
) : FragmentActivity(), RefreshSupportedActivity {

    private val logger: Logger = DependenciesHolder().logger

    protected lateinit var inflater: LayoutInflater

    @JvmField
    protected var cursor: Cursor? = null

    @JvmField
    protected var adapter: ListAdapter? = null

    @JvmField
    protected var db: DatabaseAdapter = DependenciesHolder().databaseAdapter

    @JvmField
    protected var bAdd: ImageButton? = null

    @JvmField
    protected var enablePin: Boolean = true

    @JvmField
    protected var savedListIndex: Int = 0

    @JvmField
    protected var savedListTop: Int = 0

    private var listViewInternal: ListView? = null

    fun getListView(): ListView =
        requireNotNull(listViewInternal) { "ListView is not initialized yet" }

    fun getListAdapter(): ListAdapter? = adapter

    fun setListAdapter(adapter: ListAdapter?) {
        this.adapter = adapter
        listViewInternal?.adapter = adapter
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)

        setContentView(contentId)

        val lv = findViewById<ListView>(android.R.id.list)
        listViewInternal = lv
        findViewById<View?>(android.R.id.empty)?.let { emptyView ->
            lv.emptyView = emptyView
        }
        lv.setOnItemClickListener { parent, view, position, id ->
            onListItemClick(parent as ListView, view, position, id)
        }

        db.open()

        inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        internalOnCreate(savedInstanceState)

        cursor = createCursor()
        cursor?.let {
            @Suppress("DEPRECATION")
            startManagingCursor(it)
        }

        recreateAdapter()
        lv.setOnItemLongClickListener { _, view, position, id ->
            val popupMenu = PopupMenu(this@AbstractListActivity, view)
            val menu = popupMenu.menu
            val menus = createContextMenus(id)
            var i = 0
            for (m in menus) {
                if (m.enabled) {
                    menu.add(0, m.menuId, i++, m.titleId)
                }
            }
            popupMenu.setOnMenuItemClickListener { item ->
                onPopupItemSelected(item.itemId, view, position, id)
            }
            popupMenu.show()
            true
        }
    }

    protected open fun recreateAdapter() {
        adapter = createAdapter(cursor)
        setListAdapter(adapter)
    }

    protected abstract fun createCursor(): Cursor?

    protected abstract fun createAdapter(cursor: Cursor?): ListAdapter?

    protected open fun internalOnCreate(savedInstanceState: Bundle?) {
        bAdd = findViewById(R.id.bAdd)
        bAdd?.setOnClickListener { addItem() }
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    protected open fun saveListScrollPosition() {
        try {
            val lv = listViewInternal
            if (lv != null) {
                val v = lv.getChildAt(0)
                if (v != null) {
                    savedListIndex = lv.firstVisiblePosition
                    savedListTop = v.top - lv.paddingTop
                }
            }
        } catch (ignored: Exception) {
        }
    }

    override fun onPause() {
        saveListScrollPosition()
        super.onPause()
        if (enablePin) PinProtection.lock(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveListScrollPosition()
        outState.putInt(STATE_SAVED_LIST_INDEX, savedListIndex)
        outState.putInt(STATE_SAVED_LIST_TOP, savedListTop)
    }

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)
        savedListIndex = state.getInt(STATE_SAVED_LIST_INDEX, 0)
        savedListTop = state.getInt(STATE_SAVED_LIST_TOP, 0)
    }

    override fun onResume() {
        super.onResume()
        if (enablePin) PinProtection.unlock(this)
    }

    protected open fun createContextMenus(id: Long): MutableList<MenuItemInfo> {
        val menus = LinkedList<MenuItemInfo>()
        menus.add(MenuItemInfo(MENU_VIEW, R.string.view))
        menus.add(MenuItemInfo(MENU_EDIT, R.string.edit))
        menus.add(MenuItemInfo(MENU_DELETE, R.string.delete))
        return menus
    }

    open fun onPopupItemSelected(itemId: Int, view: View, position: Int, id: Long): Boolean {
        when (itemId) {
            MENU_VIEW -> {
                viewItem(view, position, id)
                return true
            }
            MENU_EDIT -> {
                editItem(view, position, id)
                return true
            }
            MENU_DELETE -> {
                deleteItem(view, position, id)
                return true
            }
        }
        return false
    }

    protected open fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        onItemClick(v, position, id)
    }

    protected open fun onItemClick(v: View, position: Int, id: Long) {
        viewItem(v, position, id)
    }

    protected open fun addItem() {}

    protected abstract fun deleteItem(v: View?, position: Int, id: Long)

    protected abstract fun editItem(v: View?, position: Int, id: Long)

    protected abstract fun viewItem(v: View?, position: Int, id: Long)

    override fun recreateCursor() {
        logger.i("Recreating cursor")
        val lv = listViewInternal
        if (lv != null && lv.getChildAt(0) != null) {
            saveListScrollPosition()
        }

        cursor?.let {
            @Suppress("DEPRECATION")
            stopManagingCursor(it)
            it.close()
        }
        cursor = createCursor()
        cursor?.let { c ->
            @Suppress("DEPRECATION")
            startManagingCursor(c)
            val currentAdapter = adapter
            if (currentAdapter is CursorAdapter) {
                currentAdapter.changeCursor(c)
            } else {
                recreateAdapter()
            }
        }
        val currentListAdapter = lv?.adapter
        if (lv != null && currentListAdapter != null && currentListAdapter.count > 0) {
            val restoreIndex = savedListIndex.coerceAtMost(currentListAdapter.count - 1)
            if (restoreIndex >= 0) {
                lv.setSelectionFromTop(restoreIndex, savedListTop)
            }
        }
    }

    override fun integrityCheck() {}

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            recreateCursor()
        }
    }

    companion object {
        const val MENU_VIEW: Int = Menu.FIRST + 1
        const val MENU_EDIT: Int = Menu.FIRST + 2
        const val MENU_DELETE: Int = Menu.FIRST + 3
        const val MENU_ADD: Int = Menu.FIRST + 4

        private const val STATE_SAVED_LIST_INDEX = "STATE_SAVED_LIST_INDEX"
        private const val STATE_SAVED_LIST_TOP = "STATE_SAVED_LIST_TOP"
    }
}
