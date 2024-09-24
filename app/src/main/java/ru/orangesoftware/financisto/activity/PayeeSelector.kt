package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.widget.ArrayAdapter
import android.widget.ListAdapter

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.TransactionUtils

class PayeeSelector<A : AbstractActivity> @JvmOverloads constructor(
    activity: A,
    entityManager: MyEntityManager,
    layout: ActivityLayout,
    emptyId: Int = R.string.no_payee,
) : MyEntitySelector<Payee,A>(
    Payee::class.java,
    activity,
    entityManager,
    layout,
    MyPreferences.isShowPayee(activity),
    R.id.payee,
    R.id.payee_add,
    R.id.payee_clear,
    R.string.payee,
    emptyId,
    R.id.payee_show_list,
    R.id.payee_close_filter,
    R.id.payee_show_filter,
) {

    override fun getEditActivityClass(): Class<*> = PayeeActivity::class.java

    override fun fetchEntities(entityManager: MyEntityManager) = entityManager.allActivePayeeList

    override fun createAdapter(activity: Activity?, entities: MutableList<Payee>?): ListAdapter =
        TransactionUtils.createPayeeAdapter(activity, entities)

    override fun createFilterAdapter(): ArrayAdapter<Payee> =
        TransactionUtils.payeeFilterAdapter(activity, em)

    override fun isListPickConfigured(): Boolean = MyPreferences.isPayeeSelectorList(activity)
}
