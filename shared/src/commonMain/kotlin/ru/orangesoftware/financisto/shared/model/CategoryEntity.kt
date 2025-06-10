/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.shared.model

// import javax.persistence.Column; // JPA
// import javax.persistence.Transient; // JPA - use Kotlin's @Transient
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.ForeignKey

// No @Entity annotation here if it's intended to be part of a table defined by a subclass (like Category)
// Or, if it IS an entity, it needs its own table name. Assuming common fields for other entities for now.
// For this example, let's assume CategoryEntity itself is NOT an entity, but its fields are inherited by @Entity classes.
// If CategoryEntity itself was an @Entity, subclasses would need @Entity(tableName="...") and potentially an inheritance strategy.
open class CategoryEntity<T : CategoryEntity<T>> : MyEntity() { // Made open

    @Ignore // Parent object reference should not be persisted directly as a column
    var parent: T? = null

    @ColumnInfo(name = "left_node") // Renamed to avoid SQL keyword 'left'
    var left: Int = 1

    @ColumnInfo(name = "right_node") // Renamed to avoid SQL keyword 'right'
    var right: Int = 2

    @ColumnInfo(name = "type")
    var type: Int = TYPE_EXPENSE

    @Ignore // Children list should not be persisted directly as a column
    var children: CategoryTree<T>? = null // CategoryTree will need to be KMP compatible or replaced

    // This field would represent the foreign key to the parent in the database
    @ColumnInfo(name = "parent_id")
    var parentId: Long? = null // Nullable if it can be a top-level category

    fun getParentId(): Long {
        return parent?.id ?: 0L
    }

    @Suppress("UNCHECKED_CAST")
    fun addChild(category: T) {
        if (children == null) {
            children = CategoryTree() // Assumes CategoryTree has a KMP-compatible constructor
        }
        category.parent = this as T // It's "this" instance of T
        category.parentId = this.id // Set the parentId foreign key
        category.type = this.type
        children?.add(category)
    }

    fun removeChild(category: T) {
        children?.remove(category)
    }

    fun hasChildren(): Boolean {
        return children?.isNotEmpty() ?: false
    }

    fun isExpense(): Boolean {
        return type == TYPE_EXPENSE
    }

    fun isIncome(): Boolean {
        return type == TYPE_INCOME
    }

    fun makeThisCategoryIncome() {
        this.type = TYPE_INCOME
    }

    fun makeThisCategoryExpense() {
        this.type = TYPE_EXPENSE
    }

    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}

// Placeholder for CategoryTree. This will need to be properly implemented or moved.
// For now, just enough to make CategoryEntity compile.
open class CategoryTree<T : CategoryEntity<T>> : ArrayList<T>() {
    // Basic ArrayList functionality is usually fine for KMP.
    // If it has custom logic, that needs to be ported.
}
