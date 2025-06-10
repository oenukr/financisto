package ru.orangesoftware.financisto.repository

import ru.orangesoftware.financisto.db.dao.AccountDao
import ru.orangesoftware.financisto.db.dao.CurrencyDao
import ru.orangesoftware.financisto.db.dao.ExchangeRateDao
import ru.orangesoftware.financisto.db.entity.AccountEntity
import ru.orangesoftware.financisto.db.entity.CurrencyEntity
import ru.orangesoftware.financisto.model.Total // Existing model
import ru.orangesoftware.financisto.model.TotalError
import java.math.BigDecimal

class AccountRepository(
    private val accountDao: AccountDao,
    private val currencyDao: CurrencyDao,
    private val exchangeRateDao: ExchangeRateDao
) {
    // Simplified version of DatabaseAdapter.getAccountsTotalInHomeCurrency()
    suspend fun getAccountsTotalInHomeCurrency(): Total {
        val homeCurrencyEntity = currencyDao.getDefaultCurrency()
        if (homeCurrencyEntity == null) {
            // Or handle as an error state appropriately
            return Total(null, TotalError.NO_HOME_CURRENCY)
        }
        // Create a model Currency object for the Total constructor
        val homeCurrencyModel = CurrencyEntityMapper.toModel(homeCurrencyEntity)


        // Fetch accounts that should be included
        val accounts = accountDao.getAllActiveSuspendable()

        var total = BigDecimal.ZERO
        val accountsToConvert = mutableListOf<AccountEntity>()

        for (account in accounts) {
            if (account.isIncludeIntoTotals && account.isActive) {
                if (account.currencyId == homeCurrencyEntity.id) {
                    total = total.add(BigDecimal.valueOf(account.totalAmount))
                } else {
                    accountsToConvert.add(account)
                }
            }
        }

        for (account in accountsToConvert) {
            val rateEntity = exchangeRateDao.findRateOnOrBeforeDate(
                fromCurrencyId = account.currencyId,
                toCurrencyId = homeCurrencyEntity.id,
                date = System.currentTimeMillis() // Use current date for latest rate
            )
            if (rateEntity == null) {
                val fromCurrency = currencyDao.getById(account.currencyId)
                // Or handle as an error state appropriately
                return Total(homeCurrencyModel, TotalError.lastRateError(CurrencyEntityMapper.toModel(fromCurrency)))
            }
            // Ensure not to multiply by zero rate if that's possible or invalid
            val rateValue = if (rateEntity.rate == 0.0) 1.0 else rateEntity.rate
            val convertedAmount = BigDecimal.valueOf(account.totalAmount).multiply(BigDecimal.valueOf(rateValue))
            total = total.add(convertedAmount)
        }

        val resultTotal = Total(homeCurrencyModel)
        // Assuming totalAmount in AccountEntity is in smallest currency unit (e.g., cents)
        // The Total model might expect the same.
        resultTotal.balance = total.toLong() // Ensure this aligns with how Total expects balance (cents or full units)
        return resultTotal
    }

    // Simplified mapper - can be expanded or moved
    object CurrencyEntityMapper {
        fun toModel(entity: CurrencyEntity?): ru.orangesoftware.financisto.model.Currency? {
            if (entity == null) return null
            val model = ru.orangesoftware.financisto.model.Currency() // Existing model
            model.id = entity.id
            model.name = entity.name
            model.symbol = entity.symbol
            model.rate = entity.rate
            model.isDefault = entity.isDefault
            model.symbolFormat = entity.symbolFormat
            model.isoCode = entity.isoCode
            // model.groupSeparator = entity.groupSeparator?.toString() // char to string if needed
            // model.decimalSeparator = entity.decimalSeparator?.toString() // char to string if needed
            return model
        }
    }
}
