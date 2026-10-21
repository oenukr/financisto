// SPDX-License-Identifier: GPL-2.0-only

package ru.orangesoftware.financisto.export;

import androidx.annotation.NonNull;

import java.util.Objects;

import ru.orangesoftware.financisto.model.Category;

/**
 * Created by IntelliJ IDEA.
 * User: denis.solonenko
 * Date: 5/29/12 2:29 PM
 */
public class CategoryInfo {

    public static final String SEPARATOR = ":";

    public static String buildName(Category c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.title);
        for (Category p = c.parent; p != null; p = p.parent) {
            sb.insert(0, SEPARATOR);
            sb.insert(0, p.title);
        }
        return sb.toString();
    }

    public String name;
    public boolean isIncome;

    public CategoryInfo() {}

    public CategoryInfo(String name, boolean income) {
        this.name = name;
        isIncome = income;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CategoryInfo that = (CategoryInfo) o;

        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "{"+name+"("+(isIncome?"I":"E")+"}";
    }

}
