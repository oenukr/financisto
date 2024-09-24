package ru.orangesoftware.financisto.activity

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.model.Total

class AccountListTotalsDetailsActivity : AbstractTotalsDetailsActivity(
    R.string.account_total_in_currency,
) {

    override fun getTotalInHomeCurrency(): Total = db.getAccountsTotalInHomeCurrency()

    override fun getTotals(): Array<Total> = db.getAccountsTotal()

}
