package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.widget.ArrayAdapter
import android.widget.ListAdapter

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.model.MyLocation
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.TransactionUtils

class LocationSelector<A : AbstractActivity> @JvmOverloads constructor(
    activity: A,
    db: DatabaseAdapter,
    layout: ActivityLayout,
    emptyId: Int = R.string.current_location,
) : MyEntitySelector<MyLocation, A>(
    MyLocation::class.java,
    activity,
    db,
    layout,
    MyPreferences.isShowLocation(activity),
    R.id.location,
    R.id.location_add,
    R.id.location_clear,
    R.string.location,
    emptyId,
    R.id.location_show_list,
    R.id.location_close_filter,
    R.id.location_show_filter
) {

    override fun getEditActivityClass(): Class<*> = LocationActivity::class.java

    override fun fetchEntities(em: MyEntityManager): MutableList<MyLocation> =
        em.getActiveLocationsList(true)

    override fun createAdapter(
        activity: Activity?,
        entities: MutableList<MyLocation>?,
    ): ListAdapter = TransactionUtils.createLocationAdapter(activity, entities)

    override fun createFilterAdapter(): ArrayAdapter<MyLocation> =
        TransactionUtils.locationFilterAdapter(activity, em)

    override fun isListPickConfigured(): Boolean = MyPreferences.isLocationSelectorList(activity)
}
