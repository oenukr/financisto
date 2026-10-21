// SPDX-License-Identifier: GPL-2.0-only

package ru.orangesoftware.financisto.filter;

import java.util.Objects;

import ru.orangesoftware.financisto.blotter.BlotterFilter;

/**
* Created by IntelliJ IDEA.
* User: denis.solonenko
* Date: 12/17/12 9:06 PM
*/
public class SingleCategoryCriteria extends Criteria {

    private final long categoryId;

    public SingleCategoryCriteria(long categoryId) {
        super(BlotterFilter.CATEGORY_ID, WhereFilter.Operation.EQ, String.valueOf(categoryId));
        this.categoryId = categoryId;
    }

    public String toStringExtra() {
        return BlotterFilter.CATEGORY_ID + ",EQ," +
                categoryId;
    }

    public static Criteria fromStringExtra(String extra) {
        String[] a = extra.split(",");
        return new SingleCategoryCriteria(Long.parseLong(a[2]));
    }

    public long getCategoryId() {
        return categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SingleCategoryCriteria that = (SingleCategoryCriteria) o;
        return categoryId == that.categoryId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), categoryId);
    }

}
