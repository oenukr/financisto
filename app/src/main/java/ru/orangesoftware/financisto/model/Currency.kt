package ru.orangesoftware.financisto.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import ru.orangesoftware.financisto.db.DatabaseHelper.CURRENCY_TABLE
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.orb.EntityManager.DEF_SORT_COL
import java.text.DecimalFormat
import java.text.NumberFormat

@Entity(tableName = CURRENCY_TABLE)
data class Currency @JvmOverloads constructor(
	@ColumnInfo(name = "name") var name: String = "",
	override var title: String? = "Default",
	@ColumnInfo(name = "symbol") var symbol: String = "",
	@ColumnInfo(name = "symbol_format") var symbolFormat: SymbolFormat = SymbolFormat.RS,
	@ColumnInfo(name = "is_default") var isDefault: Boolean = false,
	@ColumnInfo(name = "decimals") var decimals: Int = 2,
	@ColumnInfo(name = "decimal_separator") var decimalSeparator: String = "'.'",
	@ColumnInfo(name = "group_separator") var groupSeparator: String = "','",
	@ColumnInfo(name = DEF_SORT_COL) override var sortOrder: Long = 0,
	@Transient
	@Ignore private var format: DecimalFormat? = null,
) : MyEntity(), SortableEntity {

    override fun toString(): String = name

    fun getFormat(currencyCache: CurrencyCache): NumberFormat {
		format = format ?: currencyCache.createCurrencyFormat(this)
		return format as NumberFormat
	}

	companion object {
		val EMPTY: Currency = Currency()

		@JvmStatic
		fun defaultCurrency(): Currency {
			val currency: Currency = Currency()
			currency.id = 2
			currency.name = "USD"
			currency.title = "American Dollar"
			currency.symbol = "$"
			currency.decimals = 2
			return currency
		}
	}
}
