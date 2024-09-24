package ru.orangesoftware.financisto.activity

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.model.Payee

class PayeeListActivity : MyEntityListActivity<Payee>(
    Payee::class.java,
    R.string.no_payees,
) {

    override fun getEditActivityClass(): Class<PayeeActivity> = PayeeActivity::class.java

    override fun createBlotterCriteria(p: Payee): Criteria =
        Criteria.eq(BlotterFilter.PAYEE_ID, p.id.toString())
}
