package ru.orangesoftware.financisto.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TransactionAppFunctions : KoinComponent {

    private val db: AppFunctionDatabase by inject()

    /**
     * Creates a transaction or transfer in Financisto.
     *
     * @param context The AppFunctionContext.
     * @param amount The transaction amount (e.g. 15.50).
     * @param accountName The name of the source account (e.g. "Cash").
     * @param categoryName The optional category of the transaction (e.g. "Food").
     * @param payeeName The optional payee name (e.g. "Starbucks"). If not found, a new Payee is created.
     * @param note An optional note/description for the transaction.
     * @param isIncome True for income, false/null for expense. Ignored for transfers.
     * @param toAccountName The optional destination account name. If provided, the transaction is treated as a transfer.
     * @return The database ID of the created transaction, or -1 if execution failed.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addTransaction(
        context: AppFunctionContext,
        amount: Double,
        accountName: String,
        categoryName: String?,
        payeeName: String?,
        note: String?,
        isIncome: Boolean?,
        toAccountName: String?
    ): Long {
        return try {
            // 1. Resolve source account
            val account = db.getAccounts().firstOrNull {
                it.title.equals(accountName, ignoreCase = true)
            } ?: return -1L

            // 2. Resolve category (optional)
            val categoryId = if (!categoryName.isNullOrBlank()) {
                db.getCategories().firstOrNull {
                    it.title.equals(categoryName, ignoreCase = true)
                }?.id ?: 0L
            } else {
                0L
            }

            // 3. Resolve or create payee (optional)
            val payeeId = if (!payeeName.isNullOrBlank()) {
                val existing = db.getPayees().firstOrNull {
                    it.title.equals(payeeName, ignoreCase = true)
                }
                existing?.id ?: db.createPayee(payeeName)
            } else {
                0L
            }

            val amountInCents = Math.round(amount * 100)
            val finalNote = note ?: ""

            if (!toAccountName.isNullOrBlank()) {
                // 4a. Handle Transfer
                val toAccount = db.getAccounts().firstOrNull {
                    it.title.equals(toAccountName, ignoreCase = true)
                } ?: return -1L

                if (account.id == toAccount.id) return -1L

                db.insertOrUpdateTransaction(
                    fromAccountId = account.id,
                    toAccountId = toAccount.id,
                    categoryId = categoryId,
                    payeeId = payeeId,
                    amount = -amountInCents, // Deducted from source
                    toAmount = amountInCents,  // Added to destination
                    note = finalNote,
                    dateTime = System.currentTimeMillis()
                )
            } else {
                // 4b. Handle Regular Transaction
                val signMultiplier = if (isIncome == true) 1 else -1
                db.insertOrUpdateTransaction(
                    fromAccountId = account.id,
                    toAccountId = null,
                    categoryId = categoryId,
                    payeeId = payeeId,
                    amount = signMultiplier * amountInCents,
                    toAmount = null,
                    note = finalNote,
                    dateTime = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            -1L
        }
    }
}
