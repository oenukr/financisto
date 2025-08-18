package ru.orangesoftware.financisto.model

import androidx.room.TypeConverter

enum class SymbolFormat {

    RS {
        override fun appendSymbol(sb: StringBuilder, symbol: String) {
            sb.append(" ").append(symbol)
        }
    },
    R {
        override fun appendSymbol(sb: StringBuilder, symbol: String) {
            sb.append(symbol)
        }
    },
    LS {
        override fun appendSymbol(sb: StringBuilder, symbol: String) {
            val offset = getInsertIndex(sb)
            sb.insert(offset, " ").insert(offset, symbol)
        }
    },
    L {
        override fun appendSymbol(sb: StringBuilder, symbol: String) {
            sb.insert(getInsertIndex(sb), symbol)
        }
    };

    companion object {
        @JvmStatic
        private fun getInsertIndex(sb: StringBuilder): Int {
            if (sb.isNotEmpty()) {
                val c: Char = sb[0]
                return if (c == '+' || c == '-') 1 else 0
            }
            return 0
        }
    }

    abstract fun appendSymbol(sb: StringBuilder, symbol: String)
}

object SymbolFormatConverter {
    @TypeConverter
    fun toSymbolFormat(value: String) = enumValueOf<SymbolFormat>(value)

    @TypeConverter
    fun fromSymbolFormat(value: SymbolFormat) = value.name
}
