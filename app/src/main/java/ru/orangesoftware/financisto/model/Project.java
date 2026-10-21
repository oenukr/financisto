// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.model;

import static ru.orangesoftware.financisto.db.DatabaseHelper.PROJECT_TABLE;
import static ru.orangesoftware.orb.EntityManager.DEF_SORT_COL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = PROJECT_TABLE)
public class Project extends MyEntity implements SortableEntity {

    @Column(name = DEF_SORT_COL)
    public long sortOrder;

    public static final int NO_PROJECT_ID = 0;

    public static Project noProject() {
        Project project = new Project();
        project.id = NO_PROJECT_ID;
        project.title = "<NO_PROJECT>";
        project.isActive = true;
        return project;
    }

    @Override
    public long getSortOrder() {
        return sortOrder;
    }
}
