package ru.orangesoftware.financisto.utils

import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.model.TransactionInfo

class TransactionList(
    val transactions: List<TransactionInfo>,
    val totals: Array<Total>,
)
