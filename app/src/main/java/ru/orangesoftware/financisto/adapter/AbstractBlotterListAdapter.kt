package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.graphics.drawable.Drawable

import androidx.core.content.ContextCompat

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter

abstract class AbstractBlotterListAdapter(
	db: DatabaseAdapter,
	context: Context,
	c: Cursor,
) : AbstractGenericListAdapter(db, context, c) {

	protected val transferColor: Int = ContextCompat.getColor(context, R.color.transfer_color)
	protected val futureColor: Int = ContextCompat.getColor(context, R.color.future_color)
	protected val icBlotterIncome: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_blotter_income)
	protected val icBlotterExpense: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_blotter_expense)
	protected val icBlotterTransfer: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_blotter_transfer)
	

	abstract override fun bindView(v: GenericViewHolder?, context: Context?, cursor: Cursor?)
	
	protected fun setIcon(v: GenericViewHolder, amount: Long, transfer: Boolean) {
		// do nothing
	}
}
