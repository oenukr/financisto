package ru.orangesoftware.financisto.app

import ru.orangesoftware.financisto.appfunctions.AccountInfo
import ru.orangesoftware.financisto.appfunctions.AppFunctionDatabase
import ru.orangesoftware.financisto.appfunctions.CategoryInfo
import ru.orangesoftware.financisto.appfunctions.PayeeInfo
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus

class AppFunctionDatabaseImpl(private val db: DatabaseAdapter) : AppFunctionDatabase {
    override fun getAccounts(): List<AccountInfo> {
        return db.list(Account::class.java).map { AccountInfo(it.id, it.title) }
    }

    override fun getCategories(): List<CategoryInfo> {
        return db.list(Category::class.java).map { CategoryInfo(it.id, it.title) }
    }

    override fun getPayees(): List<PayeeInfo> {
        return db.list(Payee::class.java).map { PayeeInfo(it.id, it.title) }
    }

    override fun createPayee(name: String): Long {
        val payee = Payee().apply { title = name }
        return db.saveOrUpdate(payee)
    }

    override fun insertOrUpdateTransaction(
        fromAccountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        payeeId: Long?,
        amount: Long,
        toAmount: Long?,
        note: String,
        dateTime: Long
    ): Long {
        val transaction = Transaction().apply {
            this.isTemplate = 0
            this.fromAccountId = fromAccountId
            this.toAccountId = toAccountId ?: 0L
            this.categoryId = categoryId ?: 0L
            this.payeeId = payeeId ?: 0L
            this.fromAmount = amount
            this.toAmount = toAmount ?: 0L
            this.note = note
            this.dateTime = dateTime
            this.status = TransactionStatus.UR
        }
        return db.insertOrUpdate(transaction)
    }
}
