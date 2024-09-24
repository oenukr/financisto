package ru.orangesoftware.financisto.activity

import android.view.View

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.model.MyLocation

class LocationsListActivity : MyEntityListActivity<MyLocation>(
    MyLocation::class.java,
    R.string.no_locations,
) {

    override fun getEditActivityClass(): Class<out MyEntityActivity<MyLocation>> =
        LocationActivity::class.java

    override fun createBlotterCriteria(location: MyLocation?): Criteria =
        Criteria.eq(BlotterFilter.LOCATION_ID, location?.id.toString())

    override fun deleteItem(v: View?, position: Int, id: Long) {
        db.deleteLocation(id)
        recreateCursor()
    }
}
