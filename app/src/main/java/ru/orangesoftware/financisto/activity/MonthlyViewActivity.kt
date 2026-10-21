// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.CreditCardStatementAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.AccountType
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.utils.Utils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

@Suppress("TooManyFunctions")
class MonthlyViewActivity : FragmentActivity() {

    private val viewModel: MonthlyViewViewModel by viewModel()

    private var accountId: Long = 0
    private var account: Account? = null
    private var currency: Currency? = null
    private var isCreditCard: Boolean = false
    private var isStatementPreview: Boolean = false

    private var title: String? = null
    private var closingDay: Int = 0
    private var paymentDay: Int = 0

    private var month: Int = 0
    private var year: Int = 0
    private var closingDate: Calendar? = null

    private lateinit var utils: Utils
    private lateinit var listView: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var resultLabelTextView: TextView
    private lateinit var buttonPrevious: ImageButton
    private lateinit var buttonNext: ImageButton
    private lateinit var buttonPopup: ImageButton

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.monthly_view)

        utils = Utils(this)
        bindViews()

        if (savedInstanceState != null) {
            month = savedInstanceState.getInt(KEY_MONTH)
            year = savedInstanceState.getInt(KEY_YEAR)
        }

        intent?.let {
            accountId = it.getLongExtra(ACCOUNT_EXTRA, 0L)
            isStatementPreview = it.getBooleanExtra(BILL_PREVIEW_EXTRA, false)
        }

        initializeData()
        setupPopupMenu()
        observeUiState()
    }

    private fun bindViews() {
        listView = findViewById(android.R.id.list)
        emptyTextView = findViewById(android.R.id.empty)
        listView.emptyView = emptyTextView
        titleTextView = findViewById(R.id.monthly_view_title)
        resultTextView = findViewById(R.id.monthly_result)
        resultLabelTextView = findViewById(R.id.monthly_result_label)
        buttonPrevious = findViewById(R.id.bt_month_previous)
        buttonNext = findViewById(R.id.bt_month_next)
        buttonPopup = findViewById(R.id.bt_popup)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_MONTH, month)
        outState.putInt(KEY_YEAR, year)
    }

    override fun onPause() {
        super.onPause()
        PinProtection.lock(this)
    }

    override fun onResume() {
        super.onResume()
        PinProtection.unlock(this)
    }

    private fun initializeData() {
        account = viewModel.getAccount(accountId)

        if (month == 0 && year == 0) {
            val cal = Calendar.getInstance()
            month = cal.get(Calendar.MONTH) + 1
            year = cal.get(Calendar.YEAR)
        }

        account?.let { acc ->
            isCreditCard = AccountType.valueOf(acc.type).isCreditCard
            currency = acc.currency

            if (isCreditCard && isStatementPreview) {
                val rawTitle = getString(R.string.ccard_statement_title)
                val accountTitle = acc.title.takeIf { !it.isNullOrEmpty() } ?: acc.cardIssuer.orEmpty()
                title = rawTitle.replace(getString(R.string.ccard_par), accountTitle)
                paymentDay = acc.paymentDay
                closingDay = acc.closingDay
                setTitle(R.string.ccard_statement)
                setCCardTitle()
                setCCardInterval()
            } else {
                title = when {
                    !acc.title.isNullOrEmpty() -> acc.title
                    isCreditCard -> acc.cardIssuer
                    else -> getString(AccountType.valueOf(acc.type).titleId)
                }
                paymentDay = 1
                closingDay = DEFAULT_CLOSING_DAY
                setTitle()
                setInterval()
                resultLabelTextView.text = getString(R.string.monthly_result)
            }

            buttonPrevious.setOnClickListener {
                month--
                if (month < 1) {
                    month = MONTHS_IN_YEAR
                    year--
                }
                refreshIntervalAndView()
            }

            buttonNext.setOnClickListener {
                month++
                if (month > MONTHS_IN_YEAR) {
                    month = 1
                    year++
                }
                refreshIntervalAndView()
            }
        }
    }

    private fun refreshIntervalAndView() {
        if (isCreditCard && isStatementPreview) {
            setCCardTitle()
            setCCardInterval()
        } else {
            setTitle()
            setInterval()
        }
    }

    private fun setupPopupMenu() {
        if (isStatementPreview) {
            buttonPopup.setOnClickListener {
                val popupMenu = PopupMenu(this, buttonPopup)
                menuInflater.inflate(R.menu.statement_preview_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { item ->
                    onPopupMenuSelected(item.itemId)
                    true
                }
                popupMenu.show()
            }
        } else {
            buttonPopup.visibility = View.GONE
        }
    }

    fun onPopupMenuSelected(id: Int) {
        if (id == R.id.opt_menu_closing_day) {
            val intent = Intent(this, CCardStatementClosingDayActivity::class.java).apply {
                val regularClosingDay = getClosingDate(month, year).get(Calendar.DAY_OF_MONTH)
                closingDate?.let {
                    putExtra(CCardStatementClosingDayActivity.PERIOD_MONTH, it.get(Calendar.MONTH))
                    putExtra(CCardStatementClosingDayActivity.PERIOD_YEAR, it.get(Calendar.YEAR))
                }
                putExtra(CCardStatementClosingDayActivity.ACCOUNT, accountId)
                putExtra(CCardStatementClosingDayActivity.REGULAR_CLOSING_DAY, regularClosingDay)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_CLOSING_DAY)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val update = data.getIntExtra(CCardStatementClosingDayActivity.UPDATE_VIEW, 0)
            if (update > 0) {
                setCCardTitle()
                setCCardInterval()
            }
        }
    }

    private fun setCCardInterval() {
        val close = getClosingDate(month, year).apply {
            set(Calendar.HOUR_OF_DAY, END_OF_DAY_HOUR)
            set(Calendar.MINUTE, END_OF_DAY_MINUTE)
            set(Calendar.SECOND, END_OF_DAY_SECOND)
        }

        val open = if (month > 1) {
            getClosingDate(month - 1, year)
        } else {
            getClosingDate(MONTHS_IN_YEAR, year - 1)
        }.apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }

        closingDate = GregorianCalendar(
            close.get(Calendar.YEAR),
            close.get(Calendar.MONTH),
            close.get(Calendar.DAY_OF_MONTH),
        )

        val closePeriodKey = "${close.get(Calendar.MONTH)}${close.get(Calendar.YEAR)}".toInt()
        val cd = viewModel.getCustomClosingDay(accountId, closePeriodKey)
        if (cd > 0) {
            close.set(Calendar.DAY_OF_MONTH, cd)
        }

        val openPeriodKey = "${open.get(Calendar.MONTH)}${open.get(Calendar.YEAR)}".toInt()
        val od = viewModel.getCustomClosingDay(accountId, openPeriodKey)
        if (od > 0) {
            open.set(Calendar.DAY_OF_MONTH, od)
            open.add(Calendar.DAY_OF_MONTH, 1)
        }

        fillData(open, close)
    }

    private fun setInterval() {
        val close = GregorianCalendar(year, month - 1, getLastDayOfMonth(month, year)).apply {
            set(Calendar.HOUR_OF_DAY, END_OF_DAY_HOUR)
            set(Calendar.MINUTE, END_OF_DAY_MINUTE)
            set(Calendar.SECOND, END_OF_DAY_SECOND)
            set(Calendar.MILLISECOND, END_OF_DAY_MILLISECOND)
        }
        val open = GregorianCalendar(year, month - 1, 1)
        fillData(open, close)
    }

    private fun getClosingDate(month: Int, year: Int): Calendar {
        var m = month
        if (closingDay > paymentDay) {
            m--
        }
        val maxDay = getLastDayOfMonth(m, year)
        val day = if (closingDay > maxDay) maxDay else closingDay
        return GregorianCalendar(year, m - 1, day)
    }

    private fun getLastDayOfMonth(month: Int, year: Int): Int {
        val calCurr = GregorianCalendar.getInstance()
        calCurr.set(year, month - 1, 1)
        return calCurr.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun fillData(open: Calendar, close: Calendar) {
        account?.let { acc ->
            viewModel.loadMonthlyPreview(
                account = acc,
                isStatementPreview = isStatementPreview,
                open = open.time,
                close = close.time,
            )
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: MonthlyViewUiState) {
        when (state) {
            is MonthlyViewUiState.Loading -> {
                emptyTextView.setText(R.string.calculating)
            }
            is MonthlyViewUiState.Success -> {
                if (state.transactions.isEmpty()) {
                    displayNoTransactions()
                } else {
                    displayTransactions(state.transactions, state.total, state.isStatementPreview)
                }
            }
            is MonthlyViewUiState.Empty -> {
                displayNoTransactions()
            }
            is MonthlyViewUiState.Error -> {
                displayNoTransactions()
                emptyTextView.text = state.message ?: getString(R.string.error)
            }
        }
    }

    private fun displayTransactions(
        transactions: List<TransactionInfo>,
        total: Long,
        statementPreview: Boolean,
    ) {
        val expenses = CreditCardStatementAdapter(
            this,
            R.layout.credit_card_transaction,
            transactions,
            currency,
            accountId,
        ).apply {
            this.isStatementPreview = statementPreview
        }
        listView.adapter = expenses

        if (statementPreview) {
            utils.setAmountText(resultTextView, currency, -total, false)
            resultTextView.setTextColor(Color.BLACK)
        } else {
            if (total < 0) {
                utils.setAmountText(resultTextView, currency, -total, false)
                utils.setNegativeColor(resultTextView)
            } else {
                utils.setAmountText(resultTextView, currency, total, false)
                utils.setPositiveColor(resultTextView)
            }
        }
    }

    private fun displayNoTransactions() {
        utils.setAmountText(resultTextView, currency, 0L, false)
        resultTextView.setTextColor(Color.BLACK)
        emptyTextView.setText(R.string.no_transactions)
        listView.adapter = null
    }

    private fun setCCardTitle() {
        val date = GregorianCalendar(year, month - 1, paymentDay)
        val monthNum = date.get(Calendar.MONTH) + 1
        val monthStr = if (monthNum < TWO_DIGIT_THRESHOLD) "0$monthNum" else monthNum.toString()
        val yearStr = date.get(Calendar.YEAR).toString()
        val paymentDayStr = if (paymentDay < TWO_DIGIT_THRESHOLD) "0$paymentDay" else paymentDay.toString()
        val pd = "$paymentDayStr/$monthStr/$yearStr"

        titleTextView.text = "$title\n$pd"
        resultLabelTextView.text = "${getString(R.string.bill_on)} $pd"
    }

    private fun setTitle() {
        val date = GregorianCalendar(year, month - 1, 1)
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val pd = dateFormat.format(date.time)
        titleTextView.text = "$title\n$pd"
    }

    companion object {
        const val ACCOUNT_EXTRA = "account_id"
        const val BILL_PREVIEW_EXTRA = "bill_preview"
        private const val REQUEST_CODE_CLOSING_DAY = 16
        private const val END_OF_DAY_HOUR = 23
        private const val END_OF_DAY_MINUTE = 59
        private const val END_OF_DAY_SECOND = 59
        private const val END_OF_DAY_MILLISECOND = 999
        private const val MONTHS_IN_YEAR = 12
        private const val DEFAULT_CLOSING_DAY = 31
        private const val TWO_DIGIT_THRESHOLD = 10
        private const val KEY_MONTH = "monthly_view_month"
        private const val KEY_YEAR = "monthly_view_year"
    }
}
