package ru.orangesoftware.financisto.graph

import ru.orangesoftware.financisto.report.IncomeExpense
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max

class IncomeExpenseAmount {

    var income: BigDecimal = BigDecimal.ZERO
    var expense: BigDecimal = BigDecimal.ZERO

    fun add(amount: BigDecimal, forceIncome: Boolean) {
        if (forceIncome || amount.toLong() > 0) {
            income  = income.add(amount)
        } else {
            expense = expense.add(amount)
        }
    }

    fun max(): Long = max(abs(income.toLong()), abs(expense.toLong()))

    fun balance(): Long {
        return income.toLong() + expense.toLong()
    }

    fun filter(incomeExpense: IncomeExpense) {
        when (incomeExpense) {
            IncomeExpense.INCOME -> expense = BigDecimal.ZERO
            IncomeExpense.EXPENSE -> income = BigDecimal.ZERO
            IncomeExpense.SUMMARY -> {
                income = income.add(expense)
                expense = BigDecimal.ZERO
            }
            else -> {}
        }
    }
}
