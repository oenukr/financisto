// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.AccountListAdapter2
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.dialog.AccountInfoDialog
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.IntegrityCheckAutobackup
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.MyPreferences.isQuickMenuEnabledForAccount
import ru.orangesoftware.financisto.view.NodeInflater
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
class AccountListActivity : AbstractListActivity(R.layout.account_list) {

    private var accountActionGrid: QuickActionWidget? = null
    private var selectedId: Long = -1L

    internal var totalCalculationTask: AccountTotalsCalculationTask? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
        setupMenuButton()
        calculateTotals()
        integrityCheck()
    }

    override fun onDestroy() {
        totalCalculationTask?.cancel()
        super.onDestroy()
    }

    private fun setupUi() {
        findViewById<View>(R.id.integrity_error).setOnClickListener { v -> v.visibility = View.GONE }
        getListView().setOnItemLongClickListener { _, view, _, id ->
            selectedId = id
            prepareAccountActionGrid()
            accountActionGrid?.show(view)
            true
        }
    }

    private fun setupMenuButton() {
        val bMenu = findViewById<ImageButton>(R.id.bMenu)
        if (MyPreferences.isShowMenuButtonOnAccountsScreen(this)) {
            bMenu.setOnClickListener {
                val popupMenu = PopupMenu(this@AccountListActivity, bMenu)
                val inflater = menuInflater
                inflater.inflate(R.menu.account_list_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { item ->
                    handlePopupMenu(item.itemId)
                    true
                }
                popupMenu.show()
            }
        } else {
            bMenu.visibility = View.GONE
        }
    }

    private fun handlePopupMenu(id: Int) {
        if (id == R.id.backup) {
            MenuListItem.MENU_BACKUP.call(this)
        } else if (id == R.id.go_to_menu) {
            DependenciesHolder().greenRobotBus.post(SwitchToMenuTabEvent())
        }
    }

    protected open fun prepareAccountActionGrid() {
        val a = db.getAccount(selectedId)
        val grid = QuickActionGrid(this)
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_info, R.string.info))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_list, R.string.blotter))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_edit, R.string.edit))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_add, R.string.transaction))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_transfer, R.string.transfer))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_tick, R.string.balance))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_flash, R.string.delete_old_transactions))
        if (a != null && a.isActive) {
            grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_lock_closed, R.string.close_account))
        } else {
            grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_lock_open, R.string.reopen_account))
        }
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_trash, R.string.delete_account))
        grid.setOnQuickActionClickListener(accountActionListener)
        accountActionGrid = grid
    }

    private val accountActionListener = QuickActionWidget.OnQuickActionClickListener { _, position ->
        when (position) {
            0 -> showAccountInfo(selectedId)
            1 -> showAccountTransactions(selectedId)
            2 -> editAccount(selectedId)
            3 -> addTransaction(selectedId, TransactionActivity::class.java)
            4 -> addTransaction(selectedId, TransferActivity::class.java)
            5 -> updateAccountBalance(selectedId)
            6 -> purgeAccount()
            7 -> closeOrOpenAccount()
            8 -> deleteAccount()
        }
    }

    private fun addTransaction(accountId: Long, clazz: Class<out AbstractTransactionActivity>) {
        val intent = Intent(this, clazz)
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId)
        startActivityForResult(intent, VIEW_ACCOUNT_REQUEST)
    }

    override fun recreateCursor() {
        super.recreateCursor()
        calculateTotals()
    }

    private fun calculateTotals() {
        totalCalculationTask?.cancel()
        val totalText = findViewById<TextView?>(R.id.total)
        totalText?.setOnClickListener { showTotals() }
        val task = AccountTotalsCalculationTask(this, db, totalText)
        totalCalculationTask = task
        task.execute(lifecycleScope)
    }

    private fun showTotals() {
        val intent = Intent(this, AccountListTotalsDetailsActivity::class.java)
        startActivityForResult(intent, -1)
    }

    class AccountTotalsCalculationTask @JvmOverloads constructor(
        context: Context?,
        private val db: DatabaseAdapter,
        totalText: TextView?,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : TotalCalculationTask(context, totalText, ioDispatcher) {

        override fun getTotalInHomeCurrency(): Total =
            db.accountsTotalInHomeCurrency

        override fun getTotals(): Array<Total> =
            emptyArray()
    }

    override fun createAdapter(cursor: Cursor?): ListAdapter? =
        AccountListAdapter2(this, cursor)

    override fun createCursor(): Cursor? {
        return if (MyPreferences.isHideClosedAccounts(this)) {
            db.allActiveAccounts
        } else {
            db.allAccounts
        }
    }

    override fun createContextMenus(id: Long): MutableList<MenuItemInfo> =
        ArrayList()

    override fun onPopupItemSelected(itemId: Int, view: View, position: Int, id: Long): Boolean =
        true

    private fun updateAccountBalance(id: Long): Boolean {
        val a = db.getAccount(id) ?: return false
        val intent = Intent(this, TransactionActivity::class.java)
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, a.id)
        intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, a.totalAmount)
        startActivityForResult(intent, 0)
        return true
    }

    override fun addItem() {
        val intent = Intent(this@AccountListActivity, AccountActivity::class.java)
        startActivityForResult(intent, NEW_ACCOUNT_REQUEST)
    }

    override fun deleteItem(v: View?, position: Int, id: Long) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_account_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                db.deleteAccount(id)
                recreateCursor()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun editItem(v: View?, position: Int, id: Long) {
        editAccount(id)
    }

    private fun editAccount(id: Long) {
        val intent = Intent(this@AccountListActivity, AccountActivity::class.java)
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id)
        startActivityForResult(intent, EDIT_ACCOUNT_REQUEST)
    }

    private fun showAccountInfo(id: Long) {
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val nodeInflater = NodeInflater(layoutInflater)
        val accountInfoDialog = AccountInfoDialog(this, id, db, nodeInflater)
        accountInfoDialog.show()
    }

    override fun onItemClick(v: View, position: Int, id: Long) {
        if (isQuickMenuEnabledForAccount(this)) {
            selectedId = id
            prepareAccountActionGrid()
            accountActionGrid?.show(v)
        } else {
            showAccountTransactions(id)
        }
    }

    override fun viewItem(v: View?, position: Int, id: Long) {
        showAccountTransactions(id)
    }

    private fun showAccountTransactions(id: Long) {
        val account = db.getAccount(id)
        if (account != null) {
            val intent = Intent(this@AccountListActivity, BlotterActivity::class.java)
            Criteria.eq(BlotterFilter.FROM_ACCOUNT_ID, id.toString())
                .toIntent(account.title, intent)
            intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true)
            startActivityForResult(intent, VIEW_ACCOUNT_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            recreateCursor()
        }
    }

    private fun purgeAccount() {
        val intent = Intent(this, PurgeAccountActivity::class.java)
        intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, selectedId)
        startActivityForResult(intent, PURGE_ACCOUNT_REQUEST)
    }

    private fun closeOrOpenAccount() {
        val a = db.getAccount(selectedId) ?: return
        if (a.isActive) {
            AlertDialog.Builder(this)
                .setMessage(R.string.close_account_confirm)
                .setPositiveButton(R.string.yes) { _, _ -> flipAccountActive(a) }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            flipAccountActive(a)
        }
    }

    private fun flipAccountActive(a: Account) {
        a.isActive = !a.isActive
        db.saveAccount(a)
        recreateCursor()
    }

    private fun deleteAccount() {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_account_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                db.deleteAccount(selectedId)
                recreateCursor()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun integrityCheck() {
        IntegrityCheckTask(this).execute(IntegrityCheckAutobackup(TimeUnit.DAYS.toMillis(7)))
    }

    companion object {
        private const val NEW_ACCOUNT_REQUEST = 1
        const val EDIT_ACCOUNT_REQUEST = 2
        private const val VIEW_ACCOUNT_REQUEST = 3
        private const val PURGE_ACCOUNT_REQUEST = 4
    }
}
