package ru.orangesoftware.financisto.activity

import android.view.View

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.model.Project

class ProjectListActivity : MyEntityListActivity<Project>(
    Project::class.java,
    R.string.no_projects,
) {

    override fun getEditActivityClass(): Class<out ProjectActivity> =
        ProjectActivity::class.java

    override fun createBlotterCriteria(project: Project): Criteria =
        Criteria.eq(BlotterFilter.PROJECT_ID, project.id.toString())

    override fun deleteItem(v: View?, position: Int, id: Long) {
        db.deleteProject(id)
        recreateCursor()
    }
}
