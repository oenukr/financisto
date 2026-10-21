// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.model;

import javax.persistence.Column;
import javax.persistence.Transient;

public class CategoryEntity<T extends CategoryEntity<T>> extends MyEntity {

    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;

	@Transient
	public T parent;
	
	@Column(name = "left")
	public int left = 1;
	
	@Column(name = "right")
	public int right = 2;
	
    @Column(name = "type")
    public int type = TYPE_EXPENSE;

	@Transient
	public CategoryTree<T> children;

	public long getParentId() {
		return parent != null ? parent.id : 0;
	}
	
	@SuppressWarnings("unchecked")
    public void addChild(T category) {
        if (children == null) {
            children = new CategoryTree<>();
        }
        category.parent = (T) this;
        category.type = this.type;
        children.add(category);
    }

    public void removeChild(T category) {
        if (children != null) {
            children.remove(category);
        }
    }

    public boolean hasChildren() {
		return children != null && !children.isEmpty();
	}

    public boolean isExpense() {
        return type == TYPE_EXPENSE;
    }

    public boolean isIncome() {
        return type == TYPE_INCOME;
    }

    public void makeThisCategoryIncome() {
        this.type = TYPE_INCOME;
    }

    public void makeThisCategoryExpense() {
        this.type = TYPE_EXPENSE;
    }

}
