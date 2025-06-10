/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 *     Abdsandryk Souza - adding default currency and fromCursor
 ******************************************************************************/
package ru.orangesoftware.financisto.shared.model

// import static ru.orangesoftware.financisto.db.DatabaseHelper.CURRENCY_TABLE; // KMP compatible DB access will be different
// import static ru.orangesoftware.orb.EntityManager.DEF_SORT_COL; // KMP compatible DB access will be different

// import androidx.annotation.NonNull; // Android specific

import java.text.DecimalFormat
import java.text.NumberFormat

// import javax.persistence.Column; // JPA
// import javax.persistence.Entity; // JPA
// import javax.persistence.Table; // JPA
// import javax.persistence.Transient; // JPA - use Kotlin's @Transient if needed for serialization
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index

// import ru.orangesoftware.financisto.utils.CurrencyCache; // Will be handled later

@Entity(
    tableName = "currencies", // CURRENCY_TABLE replacement
    indices = [Index(value = ["name"], unique = true)] // Example: Make currency name unique
)
open class Currency : MyEntity(), SortableEntity { // Made open for potential extension

    @ColumnInfo(name = "name")
    var name: String = ""

    @ColumnInfo(name = "symbol")
    var symbol: String = ""

    // For SymbolFormat, Room would need a TypeConverter if it's not a basic type.
    // Assuming SymbolFormat is an enum and will be stored as String by default by Room.
    @ColumnInfo(name = "symbol_format")
    var symbolFormat: SymbolFormat = SymbolFormat.RS

    @ColumnInfo(name = "is_default")
    var isDefault: Boolean = false

    @ColumnInfo(name = "decimals")
    var decimals: Int = 2

    @ColumnInfo(name = "decimal_separator")
    var decimalSeparator: String = "." // Default to dot

    @ColumnInfo(name = "group_separator")
    var groupSeparator: String = "," // Default to comma

    @ColumnInfo(name = "sort_order") // DEF_SORT_COL replacement
    override val sortOrder: Long = 0L // Changed to val to match interface, and ensure it's an override

    @Ignore // Using Room's Ignore for non-persisted fields
    @Volatile
    private var format: DecimalFormat? = null

    // @NonNull // Android specific
    override fun toString(): String {
        return name
    }

    // This will need a KMP-compatible implementation
    fun getFormat(): NumberFormat {
        var f = format
        if (f == null) {
            // f = CurrencyCache.createCurrencyFormat(this); // Needs KMP adaptation
            // For now, return a basic format
            f = DecimalFormat()
            try {
                val symbols = f.decimalFormatSymbols
                if (decimalSeparator.isNotEmpty()) {
                    symbols.decimalSeparator = decimalSeparator[0]
                }
                if (groupSeparator.isNotEmpty()) {
                    symbols.groupingSeparator = groupSeparator[0]
                }
                f.decimalFormatSymbols = symbols
                f.minimumFractionDigits = decimals
                f.maximumFractionDigits = decimals
            } catch (e: Exception) {
                // Handle exception, maybe log or use defaults
            }
            format = f
        }
        return f
    }

    // override fun getSortOrder(): Long { // Removed to avoid clash with property getter
    //     return sortOrder
    // }

    companion object {
        val EMPTY = Currency().apply {
            id = 0
            name = ""
            title = "Default" // title is from MyEntity
            symbol = ""
            symbolFormat = SymbolFormat.RS
            decimals = 2
            decimalSeparator = "."
            groupSeparator = ","
        }

        fun defaultCurrency(): Currency {
            return Currency().apply {
                id = 2 // This might need to be re-evaluated, IDs are usually DB driven
                name = "USD"
                title = "American Dollar" // title is from MyEntity
                symbol = "$"
                decimals = 2
                // symbolFormat is RS by default
                // decimalSeparator is . by default
                // groupSeparator is , by default
            }
        }
    }
}
