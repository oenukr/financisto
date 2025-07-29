package ru.orangesoftware.financisto.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import ru.orangesoftware.financisto.db.DatabaseHelper.PROJECT_TABLE
import ru.orangesoftware.orb.EntityManager.DEF_SORT_COL

@Entity(tableName = PROJECT_TABLE)
class Project(
    @ColumnInfo(name = DEF_SORT_COL) override val sortOrder: Long = 0,
) : MyEntity(), SortableEntity {

    companion object {
        const val NO_PROJECT_ID: Long = 0

        @JvmStatic
        fun noProject(): Project = Project().apply {
            id = NO_PROJECT_ID
            title = "<NO_PROJECT>"
            isActive = true
        }
    }
}
