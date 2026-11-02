// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.BlotterListAdapter
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.blotter.AccountTotalCalculationTask
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.blotter.BlotterTotalCalculationTask
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.dialog.TransactionInfoDialog
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.AccountType
import ru.orangesoftware.financisto.utils.IntegrityCheckRunningBalance
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.MyPreferences.isQuickMenuEnabledForTransaction
import ru.orangesoftware.financisto.view.NodeInflater

@Suppress("TooManyFunctions", "DEPRECATION")
open class BlotterActivity : AbstractListActivity {

    @JvmField
    protected var totalText: TextView? = null

    @JvmField
    protected var bFilter: ImageButton? = null

    @JvmField
    protected var bTransfer: ImageButton? = null

    @JvmField
    protected var bTemplate: ImageButton? = null

    @JvmField
    protected var bSearch: ImageButton? = null

    @JvmField
    protected var bMenu: ImageButton? = null

    @JvmField
    protected var transactionActionGrid: QuickActionGrid? = null

    @JvmField
    protected var addButtonActionGrid: QuickActionGrid? = null

    internal var calculationTask: TotalCalculationTask? = null
        private set

    @JvmField
    protected var saveFilter: Boolean = false

    @JvmField
    protected var blotterFilter: WhereFilter = WhereFilter.empty()

    @JvmField
    protected var isAccountBlotter: Boolean = false

    @JvmField
    protected var showAllBlotterButtons: Boolean = true

    private var nodeInflater: NodeInflater? = null
    private var selectedId: Long = -1L

    constructor(layoutId: Int) : super(layoutId)

    constructor() : super(R.layout.blotter)

    protected open fun calculateTotals() {
        calculationTask?.cancel()
        val task = createTotalCalculationTask()
        calculationTask = task
        task.execute(lifecycleScope)
    }

    protected open fun createTotalCalculationTask(): TotalCalculationTask {
        val filter = WhereFilter.copyOf(blotterFilter)
        return if (filter.accountId > 0) {
            AccountTotalCalculationTask(this, db, filter, totalText)
        } else {
            BlotterTotalCalculationTask(this, db, filter, totalText)
        }
    }

    override fun recreateCursor() {
        super.recreateCursor()
        calculateTotals()
    }

    override fun onDestroy() {
        calculationTask?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        nodeInflater = NodeInflater(layoutInflater)
        integrityCheck()
    }

    override fun internalOnCreate(savedInstanceState: Bundle?) {
        super.internalOnCreate(savedInstanceState)

        bFilter = findViewById(R.id.bFilter)
        bFilter?.setOnClickListener {
            val intent = Intent(this@BlotterActivity, BlotterFilterActivity::class.java)
            blotterFilter.toIntent(intent)
            intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, isAccountBlotter && blotterFilter.accountId > 0)
            startActivityForResult(intent, FILTER_REQUEST)
        }

        totalText = findViewById(R.id.total)
        totalText?.setOnClickListener { showTotals() }

        val incomingIntent = intent
        if (incomingIntent != null) {
            blotterFilter = WhereFilter.fromIntent(incomingIntent)
            saveFilter = incomingIntent.getBooleanExtra(SAVE_FILTER, false)
            isAccountBlotter = incomingIntent.getBooleanExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, false)
        }
        if (savedInstanceState != null) {
            blotterFilter = WhereFilter.fromBundle(savedInstanceState)
        }
        if (saveFilter && blotterFilter.isEmpty) {
            blotterFilter = WhereFilter.fromSharedPreferences(getPreferences(0))
        }

        showAllBlotterButtons = !isAccountBlotter && !MyPreferences.isCollapseBlotterButtons(this)

        if (showAllBlotterButtons) {
            bTransfer = findViewById(R.id.bTransfer)
            bTransfer?.visibility = View.VISIBLE
            bTransfer?.setOnClickListener { addItem(NEW_TRANSFER_REQUEST, TransferActivity::class.java) }

            bTemplate = findViewById(R.id.bTemplate)
            bTemplate?.visibility = View.VISIBLE
            bTemplate?.setOnClickListener { createFromTemplate() }
        }

        bSearch = findViewById(R.id.bSearch)
        bSearch?.setOnClickListener { setupSearch() }

        applyFilter()
        applyPopupMenu()
        calculateTotals()
        prepareTransactionActionGrid()
        prepareAddButtonActionGrid()
    }

    private fun setupSearch() {
        val searchText = findViewById<EditText>(R.id.search_text)
        val searchLayout = findViewById<FrameLayout>(R.id.search_text_frame)
        val searchTextClearButton = findViewById<ImageButton>(R.id.search_text_clear)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        searchText.setOnFocusChangeListener { _, _ ->
            if (!searchText.hasFocus()) {
                imm.hideSoftInputFromWindow(searchLayout.windowToken, 0)
            }
        }

        searchTextClearButton.setOnClickListener { searchText.setText("") }

        if (searchLayout.isVisible) {
            imm.hideSoftInputFromWindow(searchLayout.windowToken, 0)
            searchLayout.visibility = View.GONE
            return
        }

        searchLayout.visibility = View.VISIBLE
        searchText.requestFocusFromTouch()
        imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT)

        searchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                // do nothing
            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                // do nothing
            }

            override fun afterTextChanged(editable: Editable) {
                val clearButton = findViewById<ImageButton>(R.id.search_text_clear)
                val text = editable.toString()
                blotterFilter.remove(BlotterFilter.NOTE)

                if (text.isNotEmpty()) {
                    blotterFilter.contains(BlotterFilter.NOTE, text)
                    clearButton.visibility = View.VISIBLE
                } else {
                    clearButton.visibility = View.GONE
                }

                recreateCursor()
                applyFilter()
                saveFilter()
            }
        })

        val noteCriteria = blotterFilter.get(BlotterFilter.NOTE)
        if (noteCriteria != null) {
            var searchFilterText = noteCriteria.stringValue
            if (searchFilterText.isNotEmpty()) {
                searchFilterText = searchFilterText.substring(1, searchFilterText.length - 1)
                searchText.setText(searchFilterText)
            }
        }
    }

    private fun applyPopupMenu() {
        bMenu = findViewById(R.id.bMenu)
        if (isAccountBlotter) {
            bMenu?.setOnClickListener {
                val menuButton = bMenu ?: return@setOnClickListener
                val popupMenu = PopupMenu(this@BlotterActivity, menuButton)
                val accountId = blotterFilter.accountId
                if (accountId != -1L) {
                    val account = db.getAccount(accountId)
                    if (account != null) {
                        val type = AccountType.valueOf(account.type)
                        val menuInflater = menuInflater
                        if (type.isCreditCard) {
                            menuInflater.inflate(R.menu.ccard_blotter_menu, popupMenu.menu)
                        } else {
                            menuInflater.inflate(R.menu.blotter_menu, popupMenu.menu)
                        }
                        popupMenu.setOnMenuItemClickListener { item ->
                            onPopupMenuSelected(item.itemId)
                            true
                        }
                        popupMenu.show()
                    }
                }
            }
        } else {
            bMenu?.visibility = View.GONE
        }
    }

    open fun onPopupMenuSelected(id: Int) {
        val accountId = blotterFilter.accountId
        val intent = Intent(this, MonthlyViewActivity::class.java)
        intent.putExtra(MonthlyViewActivity.ACCOUNT_EXTRA, accountId)

        if (id == R.id.opt_menu_month) {
            intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, false)
            startActivityForResult(intent, MONTHLY_VIEW_REQUEST)
        } else if (id == R.id.opt_menu_bill) {
            if (accountId != -1L) {
                val account = db.getAccount(accountId)
                if (account != null && account.paymentDay > 0 && account.closingDay > 0) {
                    intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, true)
                    startActivityForResult(intent, BILL_PREVIEW_REQUEST)
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.statement_error)
                        .setTitle(R.string.ccard_statement)
                        .setPositiveButton(R.string.ok, null)
                        .setCancelable(true)
                        .create()
                        .show()
                }
            }
        }
    }

    private fun showTotals() {
        val intent = Intent(this, BlotterTotalsDetailsActivity::class.java)
        blotterFilter.toIntent(intent)
        startActivityForResult(intent, -1)
    }

    protected open fun prepareTransactionActionGrid() {
        val grid = QuickActionGrid(this)
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_info, R.string.info))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_edit, R.string.edit))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_trash, R.string.delete))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_copy, R.string.duplicate))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_tick, R.string.clear))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_double_tick, R.string.reconcile))
        grid.setOnQuickActionClickListener(transactionActionListener)
        transactionActionGrid = grid
    }

    private val transactionActionListener = QuickActionWidget.OnQuickActionClickListener { _, position ->
        when (position) {
            0 -> showTransactionInfo(selectedId)
            1 -> editTransaction(selectedId)
            2 -> deleteTransaction(selectedId)
            3 -> duplicateTransaction(selectedId, 1)
            4 -> clearTransaction(selectedId)
            5 -> reconcileTransaction(selectedId)
        }
    }

    private fun prepareAddButtonActionGrid() {
        val grid = QuickActionGrid(this)
        grid.addQuickAction(MyQuickAction(this, R.drawable.actionbar_add_big, R.string.transaction))
        grid.addQuickAction(MyQuickAction(this, R.drawable.ic_action_transfer, R.string.transfer))
        if (addTemplateToAddButton()) {
            grid.addQuickAction(MyQuickAction(this, R.drawable.actionbar_tiles_large, R.string.template))
        } else {
            grid.setNumColumns(2)
        }
        grid.setOnQuickActionClickListener(addButtonActionListener)
        addButtonActionGrid = grid
    }

    protected open fun addTemplateToAddButton(): Boolean = true

    private val addButtonActionListener = QuickActionWidget.OnQuickActionClickListener { _, position ->
        when (position) {
            0 -> addItem(NEW_TRANSACTION_REQUEST, TransactionActivity::class.java)
            1 -> addItem(NEW_TRANSFER_REQUEST, TransferActivity::class.java)
            2 -> createFromTemplate()
        }
    }

    private fun clearTransaction(selectedId: Long) {
        BlotterOperations(this, db, selectedId).clearTransaction()
        recreateCursor()
    }

    private fun reconcileTransaction(selectedId: Long) {
        BlotterOperations(this, db, selectedId).reconcileTransaction()
        recreateCursor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        blotterFilter.toBundle(outState)
    }

    protected open fun createFromTemplate() {
        val intent = Intent(this, SelectTemplateActivity::class.java)
        startActivityForResult(intent, NEW_TRANSACTION_FROM_TEMPLATE_REQUEST)
    }

    override fun createContextMenus(id: Long): MutableList<MenuItemInfo> {
        return if (blotterFilter.isTemplate() || blotterFilter.isSchedule) {
            super.createContextMenus(id)
        } else {
            val menus = super.createContextMenus(id)
            menus.add(MenuItemInfo(MENU_DUPLICATE, R.string.duplicate))
            menus.add(MenuItemInfo(MENU_SAVE_AS_TEMPLATE, R.string.save_as_template))
            menus
        }
    }

    override fun onPopupItemSelected(itemId: Int, view: View, position: Int, id: Long): Boolean {
        return if (super.onPopupItemSelected(itemId, view, position, id)) {
            true
        } else {
            when (itemId) {
                MENU_DUPLICATE -> {
                    duplicateTransaction(id, 1)
                    true
                }
                MENU_SAVE_AS_TEMPLATE -> {
                    BlotterOperations(this, db, id).duplicateAsTemplate()
                    Toast.makeText(this, R.string.save_as_template_success, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun duplicateTransaction(id: Long, multiplier: Int): Long {
        val newId = BlotterOperations(this, db, id).duplicateTransaction(multiplier)
        val toastText = if (multiplier > 1) {
            getString(R.string.duplicate_success_with_multiplier, multiplier)
        } else {
            getString(R.string.duplicate_success)
        }
        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show()
        recreateCursor()
        AccountWidget.updateWidgets(this@BlotterActivity)
        return newId
    }

    override fun addItem() {
        if (showAllBlotterButtons) {
            addItem(NEW_TRANSACTION_REQUEST, TransactionActivity::class.java)
        } else {
            val addBtn = bAdd ?: return
            addButtonActionGrid?.show(addBtn)
        }
    }

    protected open fun addItem(requestId: Int, clazz: Class<out AbstractTransactionActivity>) {
        val intent = Intent(this@BlotterActivity, clazz)
        val accountId = blotterFilter.accountId
        if (accountId != -1L) {
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId)
        }
        intent.putExtra(TransactionActivity.TEMPLATE_EXTRA, blotterFilter.getIsTemplate())
        startActivityForResult(intent, requestId)
    }

    override fun createCursor(): Cursor? {
        return if (isAccountBlotter) {
            db.getBlotterForAccount(blotterFilter)
        } else {
            db.getBlotter(blotterFilter)
        }
    }

    override fun createAdapter(cursor: Cursor?): ListAdapter? {
        return if (isAccountBlotter) {
            TransactionsListAdapter(this, db, cursor)
        } else {
            BlotterListAdapter(this, db, cursor)
        }
    }

    override fun deleteItem(v: View?, position: Int, id: Long) {
        deleteTransaction(id)
    }

    private fun deleteTransaction(id: Long) {
        BlotterOperations(this, db, id).deleteTransaction()
    }

    open fun afterDeletingTransaction(id: Long) {
        recreateCursor()
        AccountWidget.updateWidgets(this)
    }

    override fun editItem(v: View?, position: Int, id: Long) {
        editTransaction(id)
    }

    private fun editTransaction(id: Long) {
        BlotterOperations(this, db, id).editTransaction()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILTER_REQUEST) {
            if (resultCode == RESULT_FIRST_USER) {
                blotterFilter.clear()
            } else if (resultCode == RESULT_OK && data != null) {
                blotterFilter = WhereFilter.fromIntent(data)
            }
            if (saveFilter) {
                saveFilter()
            }
            applyFilter()
            recreateCursor()
        } else if (resultCode == RESULT_OK && requestCode == NEW_TRANSACTION_FROM_TEMPLATE_REQUEST) {
            if (data != null) {
                createTransactionFromTemplate(data)
            }
        }
        if (resultCode == RESULT_OK || resultCode == RESULT_FIRST_USER) {
            calculateTotals()
        }
    }

    private fun createTransactionFromTemplate(data: Intent) {
        val templateId = data.getLongExtra(SelectTemplateActivity.TEMPATE_ID, -1L)
        val multiplier = data.getIntExtra(SelectTemplateActivity.MULTIPLIER, 1)
        val edit = data.getBooleanExtra(SelectTemplateActivity.EDIT_AFTER_CREATION, false)
        if (templateId > 0) {
            val id = duplicateTransaction(templateId, multiplier)
            val t = db.getTransaction(id)
            if (t.fromAmount == 0L || edit) {
                BlotterOperations(this, db, id).asNewFromTemplate().editTransaction()
            }
        }
    }

    private fun saveFilter() {
        val preferences = getPreferences(0)
        blotterFilter.toSharedPreferences(preferences)
    }

    protected open fun applyFilter() {
        val accountId = blotterFilter.accountId
        if (accountId != -1L) {
            val a = db.getAccount(accountId)
            bAdd?.visibility = if (a != null && a.isActive) View.VISIBLE else View.GONE
            if (showAllBlotterButtons) {
                bTransfer?.visibility = if (a != null && a.isActive) View.VISIBLE else View.GONE
            }
        }
        val title = blotterFilter.title
        if (title != null) {
            this.title = "${getString(R.string.blotter)} : $title"
        }
        updateFilterImage()
    }

    protected open fun updateFilterImage() {
        bFilter?.let { FilterState.updateFilterColor(this, blotterFilter, it) }
    }

    override fun onItemClick(v: View, position: Int, id: Long) {
        if (isQuickMenuEnabledForTransaction(this)) {
            selectedId = id
            transactionActionGrid?.show(v)
        } else {
            showTransactionInfo(id)
        }
    }

    override fun viewItem(v: View?, position: Int, id: Long) {
        showTransactionInfo(id)
    }

    private fun showTransactionInfo(id: Long) {
        val transactionInfoView = TransactionInfoDialog(this, db, nodeInflater)
        transactionInfoView.show(this, id)
    }

    override fun integrityCheck() {
        IntegrityCheckTask(this).execute(IntegrityCheckRunningBalance(db))
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val searchLayout = findViewById<FrameLayout?>(R.id.search_text_frame)
        if (searchLayout != null && searchLayout.isVisible) {
            searchLayout.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val SAVE_FILTER: String = "saveFilter"
        const val EXTRA_FILTER_ACCOUNTS: String = "filterAccounts"

        private const val NEW_TRANSACTION_REQUEST: Int = 1
        private const val NEW_TRANSFER_REQUEST: Int = 3
        private const val NEW_TRANSACTION_FROM_TEMPLATE_REQUEST: Int = 5
        private const val MONTHLY_VIEW_REQUEST: Int = 6
        private const val BILL_PREVIEW_REQUEST: Int = 7

        const val FILTER_REQUEST: Int = 6
        private const val MENU_DUPLICATE: Int = MENU_ADD + 1
        private const val MENU_SAVE_AS_TEMPLATE: Int = MENU_ADD + 2
    }
}
