// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.model;

import static ru.orangesoftware.financisto.db.DatabaseHelper.PAYEE_TABLE;
import static ru.orangesoftware.orb.EntityManager.DEF_SORT_COL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = PAYEE_TABLE)
public class Payee extends MyEntity implements SortableEntity {

    public static final Payee EMPTY = new Payee();

    static {
        EMPTY.id = 0;
        EMPTY.title = "No payee";
    }

    @Column(name = "last_category_id")
    public long lastCategoryId;

    @Column(name = DEF_SORT_COL)
    public long sortOrder;

    @Override
    public long getSortOrder() {
        return sortOrder;
    }
}
