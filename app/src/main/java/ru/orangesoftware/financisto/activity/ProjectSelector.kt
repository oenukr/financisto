package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.widget.ArrayAdapter
import android.widget.ListAdapter

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.TransactionUtils

class ProjectSelector<A : AbstractActivity> @JvmOverloads constructor(
    activity: A,
    db: DatabaseAdapter,
    layout: ActivityLayout,
    emptyId: Int = R.string.no_project,
) : MyEntitySelector<Project, A>(
    Project::class.java,
    activity,
    db,
    layout,
    MyPreferences.isShowProject(activity),
    R.id.project,
    R.id.project_add,
    R.id.project_clear,
    R.string.project,
    emptyId,
    R.id.project_show_list,
    R.id.project_close_filter,
    R.id.project_show_filter,
) {

    override fun getEditActivityClass(): Class<*> = ProjectActivity::class.java

    override fun fetchEntities(entityManager: MyEntityManager): List<Project> =
        em.getActiveProjectsList(true)

    override fun createAdapter(activity: Activity?, entities: MutableList<Project>?): ListAdapter =
        TransactionUtils.createProjectAdapter(activity, entities)

    override fun createFilterAdapter(): ArrayAdapter<Project> =
        TransactionUtils.projectFilterAdapter(activity, em)

    override fun isListPickConfigured(): Boolean = MyPreferences.isProjectSelectorList(activity)
}
