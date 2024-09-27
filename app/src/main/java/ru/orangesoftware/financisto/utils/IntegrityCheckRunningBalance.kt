package ru.orangesoftware.financisto.utils

import android.content.Context

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.utils.IntegrityCheck.Level
import ru.orangesoftware.financisto.utils.IntegrityCheck.Result

class IntegrityCheckRunningBalance(private val db: DatabaseAdapter) : IntegrityCheck {

    override fun check(context: Context): Result = if (isRunningBalanceBroken()) {
        Result(
            Level.ERROR,
            context.getString(R.string.integrity_error)
        )
    } else {
        Result.OK
    }

    private fun isRunningBalanceBroken(): Boolean {
        val accounts: List<Account> = db.getAllAccountsList()
        accounts.forEach { account ->
            val totalFromAccount: Long = account.totalAmount
            val totalFromRunningBalance: Long = db.getLastRunningBalanceForAccount(account)
            if (totalFromAccount != totalFromRunningBalance) {
                return true
            }
        }
        return false
    }

}
