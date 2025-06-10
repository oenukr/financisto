package ru.orangesoftware.financisto.mapper

import ru.orangesoftware.financisto.db.entity.CurrencyEntity
import ru.orangesoftware.financisto.model.Currency // Old model

object CurrencyEntityMapper {
    fun toModel(entity: CurrencyEntity?): Currency? {
        if (entity == null) return null
        val model = Currency()
        model.id = entity.id
        model.name = entity.name
        model.symbol = entity.symbol
        model.rate = entity.rate // Ensure this is used if AmountInput needs it
        model.isDefault = entity.isDefault
        model.isoCode = entity.isoCode
        model.symbolFormat = entity.symbolFormat
        // Decimal and group separators are Chars in Entity, String in old Model.
        // Adjust if AmountInput relies on these from the old model.
        model.decimalSeparator = entity.decimalSeparator?.toString()
        model.groupSeparator = entity.groupSeparator?.toString()
        return model
    }
}
