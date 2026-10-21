// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.adapter.async

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.SmsDragListActivity
import ru.orangesoftware.financisto.activity.SmsTemplateActivity
import ru.orangesoftware.financisto.adapter.dragndrop.ItemTouchHelperAdapter
import ru.orangesoftware.financisto.adapter.dragndrop.ItemTouchHelperViewHolder
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.SmsTemplate
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.MenuItemInfo
import java.util.concurrent.atomic.AtomicLong

class SmsTemplateListAsyncAdapter(
    chunkSize: Int,
    private val db: DatabaseAdapter,
    itemSource: SmsTemplateListSource,
    recyclerView: RecyclerView,
    private val activity: SmsDragListActivity,
) : AsyncAdapter<SmsTemplate, SmsTemplateListAsyncAdapter.LocalViewHolder>(chunkSize, itemSource, recyclerView),
    ItemTouchHelperAdapter {

    private val logger: Logger = DependenciesHolder().logger
    private val context: Context = recyclerView.context
    private val draggedItemId = AtomicLong(0)

    companion object {
        const val MENU_EDIT: Int = Menu.FIRST + 1
        const val MENU_DUPLICATE: Int = Menu.FIRST + 2
        const val MENU_DELETE: Int = Menu.FIRST + 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocalViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.generic_list_item, parent, false)
        view.setOnClickListener { clickedView ->
            val popupMenu = PopupMenu(context, clickedView)
            var i = 0
            for (m in createContextMenus()) {
                if (m.enabled) {
                    popupMenu.menu.add(0, m.menuId, i++, m.titleId)
                }
            }
            popupMenu.setOnMenuItemClickListener { item -> onItemAction(item.itemId, clickedView) }
            popupMenu.show()
        }
        return LocalViewHolder(view)
    }

    protected fun onItemAction(menuId: Int, itemView: View): Boolean {
        val id = itemView.getTag(R.id.sms_tpl_id) as? Long ?: return false
        when (menuId) {
            MENU_EDIT -> {
                editItem(id)
                return true
            }
            MENU_DUPLICATE -> {
                activity.lifecycleScope.launch {
                    val newId = withContext(Dispatchers.IO) {
                        db.duplicateSmsTemplateBelowOriginal(id)
                    }
                    if (newId > 0) {
                        Toast.makeText(activity, R.string.duplicate_sms_template, Toast.LENGTH_LONG).show()
                        reloadAsyncSource()
                    }
                }
                return true
            }
            MENU_DELETE -> {
                deleteItem(id, -1)
                return true
            }
        }
        return false
    }

    protected fun createContextMenus(): List<MenuItemInfo> {
        return listOf(
            MenuItemInfo(MENU_EDIT, R.string.edit),
            MenuItemInfo(MENU_DUPLICATE, R.string.duplicate),
            MenuItemInfo(MENU_DELETE, R.string.delete),
        )
    }

    private fun editItem(id: Long) {
        val intent = Intent(activity, SmsTemplateActivity::class.java)
        intent.putExtra(DatabaseHelper.SmsTemplateColumns._id.name, id)
        activity.startActivityForResult(intent, SmsDragListActivity.EDIT_REQUEST_CODE)
    }

    private fun deleteItem(id: Long, position: Int) {
        AlertDialog.Builder(context)
            .setTitle(R.string.delete)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.sms_delete_alert)
            .setPositiveButton(R.string.delete) { _, _ -> deleteTemplate(id) }
            .setNegativeButton(R.string.cancel) { _, _ -> revertSwipeBack() }
            .setOnCancelListener { revertSwipeBack() }
            .show()
    }

    private fun deleteTemplate(id: Long) {
        activity.lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                db.delete(SmsTemplate::class.java, id)
            }
            logger.d("deleted: %d", res)
            if (res > 0) {
                reloadAsyncSource()
            }
        }
    }

    private fun updateSortOrder(srcId: Long, targetId: Long) {
        activity.lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                db.moveItemByChangingOrder(SmsTemplate::class.java, srcId, targetId)
            }
            logger.d("moved finished: %s", res)
            if (res) {
                reloadVisibleItems()
            }
        }
    }

    fun revertSwipeBack() {
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: LocalViewHolder, position: Int) {
        val item = listUtil.getItem(position)
        holder.bindView(item, position)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        val itemSrc = listUtil.getItem(fromPosition)
        val itemTarget = listUtil.getItem(toPosition)
        if (itemSrc != null && itemTarget != null) {
            draggedItemId.set(itemTarget.id)
            logger.d("dragged %s item to %s item", itemSrc.id, itemTarget.id)
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onItemDismiss(position: Int, dir: Int) {
        logger.d(
            "swipped %s pos to %s (%s)",
            position,
            if (dir == ItemTouchHelper.START) "left" else if (dir == ItemTouchHelper.END) "right" else "??",
            dir,
        )

        val item = listUtil.getItem(position) ?: return
        val itemId = item.id
        when (dir) {
            ItemTouchHelper.START -> editItem(itemId)
            ItemTouchHelper.END -> deleteItem(itemId, position)
            else -> logger.e("unknown move: %d", dir)
        }
    }

    inner class LocalViewHolder(view: View) : RecyclerView.ViewHolder(view), ItemTouchHelperViewHolder {
        val lineView: TextView = view.findViewById(R.id.line1)
        val labelView: TextView = view.findViewById(R.id.label)
        val numberView: TextView = view.findViewById(R.id.number)
        val amountView: TextView = view.findViewById(R.id.date)
        val iconView: ImageView = view.findViewById(R.id.icon)

        fun bindView(item: SmsTemplate?, ignore: Int?) {
            if (item != null) {
                itemView.setTag(R.id.sms_tpl_id, item.id)
                lineView.text = item.title
                numberView.text = item.template
                amountView.visibility = View.VISIBLE
                amountView.text = Category.getTitle(item.categoryName, item.categoryLevel)
            }
        }

        override fun onItemSelected() {
            logger.i("selected: %s", numberView.text)
        }

        override fun onItemClear() {
            val targetId = draggedItemId.get()
            if (targetId > 0) {
                val srcId = itemView.getTag(R.id.sms_tpl_id) as? Long ?: return
                logger.d("`%s` moving to `%s`...", numberView.text, targetId)
                updateSortOrder(srcId, targetId)
                draggedItemId.set(0)
            }
        }
    }
}
