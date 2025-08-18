package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.text.format.DateUtils
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseHelper.BlotterColumns
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.StringUtils.capitalize
import ru.orangesoftware.financisto.utils.TransactionTitleUtils.generateTransactionTitle
import ru.orangesoftware.financisto.utils.Utils

class TransactionsListAdapter(
    context: Context,
    c: Cursor?,
) : BlotterListAdapter(context, c) {

    override fun bindView(v: BlotterViewHolder?, context: Context?, cursor: Cursor?) {
        val payee: String = cursor?.getString(BlotterColumns.payee.ordinal).orEmpty()

        val locationId: Long = cursor?.getLong(BlotterColumns.location_id.ordinal) ?: 0
        val location = if (locationId > 0) {
            cursor?.getString(BlotterColumns.location.ordinal) ?: ""
        } else {
            ""
        }

        var note: String = cursor?.getString(BlotterColumns.note.ordinal).orEmpty()
        val toAccountId: Long = cursor?.getLong(BlotterColumns.to_account_id.ordinal) ?: 0
        val toAccount: String? = cursor?.getString(BlotterColumns.to_account_title.ordinal)
        val fromAmount: Long = cursor?.getLong(BlotterColumns.from_amount.ordinal) ?: 0
        if (toAccountId > 0) {
            v?.topView?.text = context?.getString(R.string.transfer)
            note = if (fromAmount > 0) {
                "$toAccount \u00BB"
            } else {
                "\u00AB $toAccount"
            }
        } else {
            val title: String? = cursor?.getString(BlotterColumns.from_account_title.ordinal)
            v?.topView?.text = title
            v?.centerView?.setTextColor(Color.WHITE)
        }

        val categoryId: Long = cursor?.getLong(BlotterColumns.category_id.ordinal) ?: 0
        val category: String = if (categoryId != 0L) {
            cursor?.getString(BlotterColumns.category_title.ordinal) ?: ""
        } else {
            ""
        }

        bindTransactionTitle(v, payee, note, location, categoryId, category)

        val currencyId: Long = cursor?.getLong(BlotterColumns.from_account_currency_id.ordinal) ?: -1
        val c: Currency = currencyCache.getCurrency(currencyId)
        val originalCurrencyId: Long = cursor?.getLong(BlotterColumns.original_currency_id.ordinal) ?: 0
        if (originalCurrencyId > 0) {
            val originalCurrency: Currency = currencyCache.getCurrency(originalCurrencyId)
            val originalAmount: Long = cursor?.getLong(BlotterColumns.original_from_amount.ordinal) ?: 0
            u.setAmountText(currencyCache, sb, v?.rightCenterView, originalCurrency, originalAmount, c, fromAmount, true)
        } else {
            u.setAmountText(currencyCache, v?.rightCenterView, c, fromAmount, true)
        }
        if (fromAmount > 0) {
            v?.iconView?.setImageDrawable(icBlotterIncome)
            v?.iconView?.setColorFilter(u.positiveColor)
        } else if (fromAmount < 0) {
            v?.iconView?.setImageDrawable(icBlotterExpense)
            v?.iconView?.setColorFilter(u.negativeColor)
        }

        val date: Long = cursor?.getLong(BlotterColumns.datetime.ordinal) ?: 0
        v?.bottomView?.text = DateUtils.formatDateTime(
            context,
            date,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        ).capitalize()
        if (date > System.currentTimeMillis()) {
            u.setFutureTextColor(v?.bottomView)
        } else {
            v?.bottomView?.setTextColor(v.topView.textColors.defaultColor)
        }

        val balance: Long = cursor?.getLong(BlotterColumns.from_account_balance.ordinal) ?: 0
        v?.rightView?.text = Utils.amountToString(currencyCache, c, balance, false)
        removeRightViewIfNeeded(v)
        setIndicatorColor(v, cursor)
    }

    private fun bindTransactionTitle(
        v: BlotterViewHolder?,
        payee: String,
        note: String,
        location: String,
        categoryId: Long,
        category: String,
    ) {
        v?.centerView?.text =
            generateTransactionTitle(sb, payee, note, location, categoryId, category)
        sb.setLength(0)
    }
}
