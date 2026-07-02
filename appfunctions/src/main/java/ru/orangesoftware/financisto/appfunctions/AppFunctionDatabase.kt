package ru.orangesoftware.financisto.appfunctions

data class AccountInfo(val id: Long, val title: String)
data class CategoryInfo(val id: Long, val title: String)
data class PayeeInfo(val id: Long, val title: String)

interface AppFunctionDatabase {
    fun getAccounts(): List<AccountInfo>
    fun getCategories(): List<CategoryInfo>
    fun getPayees(): List<PayeeInfo>
    fun createPayee(name: String): Long
    fun insertOrUpdateTransaction(
        fromAccountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        payeeId: Long?,
        amount: Long,
        toAmount: Long?,
        note: String,
        dateTime: Long
    ): Long
}
