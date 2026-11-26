// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.LocationOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

internal object TransactionScreenshotFixtures {
    private const val FIXED_DATE_TIME_MILLIS = 1715774400000L

    private val defaultCatalog = TransactionCatalog(
        accounts = listOf(
            AccountOption(
                id = 1L,
                title = "Main Checking",
                typeLabel = "Bank",
                currencyId = 1L,
                currencyCode = "USD",
                currencySymbol = "$",
                currencyDecimals = 2,
                balanceMinor = 245075L,
            ),
            AccountOption(
                id = 2L,
                title = "Euro Travel",
                typeLabel = "Cash",
                currencyId = 2L,
                currencyCode = "EUR",
                currencySymbol = "€",
                currencyDecimals = 2,
                balanceMinor = 184050L,
            ),
        ),
        currencies = listOf(
            CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
            CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
        ),
        categories = listOf(
            CategoryOption(
                id = 1L,
                parentId = 0L,
                title = "Food",
                fullPath = "Food",
                level = 0,
                isIncome = false,
            ),
            CategoryOption(
                id = 2L,
                parentId = 1L,
                title = "Groceries",
                fullPath = "Food > Groceries",
                level = 1,
                isIncome = false,
            ),
            CategoryOption(
                id = 3L,
                parentId = 1L,
                title = "Dining",
                fullPath = "Food > Dining",
                level = 1,
                isIncome = false,
            ),
        ),
        payees = listOf(PayeeOption(id = 1L, title = "Supermarket")),
        projects = listOf(
            ProjectOption(id = 1L, title = "Household"),
            ProjectOption(id = 2L, title = "Travel"),
        ),
        locations = listOf(LocationOption(id = 1L, title = "Downtown")),
        recentNotes = listOf("Weekly groceries", "Coffee"),
    )

    private val defaultPrefs = TransactionFormPreferences()

    val expenseReadyState: TransactionUiState.Ready = TransactionUiState.Ready(
        form = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            dateTimeMillis = FIXED_DATE_TIME_MILLIS,
            status = TransactionStatusOption.CLEARED,
            fromAccountId = 1L,
            primaryAmountInput = "42.50",
            primaryAmountMinor = 4250L,
            payeeId = 1L,
            categoryId = 2L,
            locationId = 1L,
            note = "Weekly groceries",
            projectId = 1L,
        ),
        initialFormSnapshot = TransactionFormState(dateTimeMillis = FIXED_DATE_TIME_MILLIS),
        catalog = defaultCatalog,
        prefs = defaultPrefs,
        isFetchingRate = false,
    )

    val transferDualCurrencyReadyState: TransactionUiState.Ready = TransactionUiState.Ready(
        form = TransactionFormState(
            mode = TransactionMode.TRANSFER,
            dateTimeMillis = FIXED_DATE_TIME_MILLIS,
            status = TransactionStatusOption.CLEARED,
            fromAccountId = 1L,
            toAccountId = 2L,
            primaryAmountInput = "120.00",
            primaryAmountMinor = 12000L,
            secondaryAmountInput = "110.40",
            secondaryAmountMinor = 11040L,
            exchangeRateInput = "0.92",
            exchangeRate = 0.92,
            note = "Currency transfer",
        ),
        initialFormSnapshot = TransactionFormState(
            mode = TransactionMode.TRANSFER,
            dateTimeMillis = FIXED_DATE_TIME_MILLIS,
        ),
        catalog = defaultCatalog,
        prefs = defaultPrefs,
        isFetchingRate = false,
    )

    val splitUnbalancedWithErrorsReadyState: TransactionUiState.Ready = TransactionUiState.Ready(
        form = TransactionFormState(
            mode = TransactionMode.EXPENSE,
            dateTimeMillis = FIXED_DATE_TIME_MILLIS,
            status = TransactionStatusOption.CLEARED,
            fromAccountId = 1L,
            primaryAmountInput = "100.00",
            primaryAmountMinor = 10000L,
            isSplitMode = true,
            splits = listOf(
                EditableSplit(
                    localId = -1L,
                    categoryId = 2L,
                    amountMinor = 5000L,
                    isIncome = false,
                    note = "Groceries",
                    projectId = 1L,
                ),
                EditableSplit(
                    localId = -2L,
                    categoryId = 3L,
                    amountMinor = 2500L,
                    isIncome = false,
                    note = "Dining",
                    projectId = TransactionIds.NO_PROJECT_ID,
                ),
            ),
            nextSplitLocalId = -3L,
        ),
        initialFormSnapshot = TransactionFormState(dateTimeMillis = FIXED_DATE_TIME_MILLIS),
        catalog = defaultCatalog,
        prefs = defaultPrefs,
        validationErrors = listOf("Split amounts do not match total amount", "Payee is required"),
        isFetchingRate = false,
    )
}
