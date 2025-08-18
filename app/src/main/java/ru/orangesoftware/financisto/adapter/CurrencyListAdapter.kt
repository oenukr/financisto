package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.orb.EntityManager

class CurrencyListAdapter(
	db: DatabaseAdapter,
	context: Context,
	c: Cursor,
	private val currencyCache: CurrencyCache,
) : AbstractGenericListAdapter(db, context, c) {
	override fun bindView(v: GenericViewHolder?, context: Context?, cursor: Cursor?) {
		val c = EntityManager.loadFromCursor(cursor, Currency::class.java)
		v?.lineView?.text = c.title
		v?.numberView?.text = c.name
		v?.amountView?.text = Utils.amountToString(currencyCache, c, 100000)
		if (c.isDefault) {
			v?.iconView?.setImageResource(R.drawable.ic_home_currency)
		} else {
			v?.iconView?.setImageDrawable(null)
		}
	}
}
