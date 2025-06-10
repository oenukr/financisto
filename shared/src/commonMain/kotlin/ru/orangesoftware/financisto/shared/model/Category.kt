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

// import android.database.Cursor; // Android specific

// import androidx.annotation.NonNull; // Android specific

// import java.util.List; // This is fine for KMP

// import javax.persistence.Column; // JPA
// import javax.persistence.Entity; // JPA
// import javax.persistence.Table; // JPA
// import javax.persistence.Transient; // JPA - use Kotlin's @Transient

// import ru.orangesoftware.financisto.db.DatabaseHelper.CategoryViewColumns; // DB specific
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index

@Entity(
    tableName = "categories", // "category" table name
    foreignKeys = [
        ForeignKey(
            entity = Category::class, // Self-referencing foreign key for parent
            parentColumns = ["id"],
            childColumns = ["parent_id"], // Assumes parent_id is in CategoryEntity and thus here
            onDelete = ForeignKey.CASCADE // Example: delete children if parent is deleted
        )
    ],
    indices = [Index(value = ["parent_id"])]
)
open class Category : CategoryEntity<Category> { // Made open

    @ColumnInfo(name = "last_location_id")
    var lastLocationId: Long = 0L

    @ColumnInfo(name = "last_project_id")
    var lastProjectId: Long = 0L

    @Ignore // Level is typically calculated, not stored directly, or if stored, not via @kotlin.jvm.Transient
    var level: Int = 0

    // @kotlin.jvm.Transient // Using Kotlin's Transient
    // var attributes: List<Attribute>? = null // Assuming Attribute will be a shared model too - COMMENTED OUT FOR NOW
    // TODO: Move Attribute.java and uncomment

    @Ignore // Tag is likely a runtime or calculated property
    var tag: String? = null

    // Constructor matching super with no arguments
    constructor() : super() {
        // Initialize parentId from CategoryEntity if it's not set by default (it is nullable Long? now)
        // this.parentId = null // Or some default if necessary, but it's already nullable
    }

    constructor(id: Long) : super() { // Call super constructor
        this.id = id // Set id from MyEntity
        // this.parentId = null // Or some default if necessary
    }

    // @NonNull // Android specific
    override fun toString(): String {
        return "[" +
                "id=" + id +
                ",parentId=" + parentId + // Use parentId from CategoryEntity
                ",title=" + title +
                ",level=" + level + // level is @Ignore'd, will be 0 unless set at runtime
                ",left=" + left + // left_node
                ",right=" + right + // right_node
                ",type=" + type +
                "]"
    }

    // override fun getTitle(): String { // title in MyEntity is now String?, this might need adjustment based on actual usage
    //     return getTitle(title, level)
    // }
    // Re-evaluating getTitle: MyEntity.title is String?, CategoryEntity inherits it.
    // Category.getTitle() might be intended to provide a non-null version or formatted version.
    // For now, let's assume it should still return a String, potentially empty if title is null.
    fun getFormattedTitle(): String { // Renamed from getTitle to avoid clash
        return Companion.getTitle(super.title, level) // Explicitly use super.title which is nullable, call companion method
    }

    fun copyTypeFromParent() {
        parent?.let { // parent is inherited from CategoryEntity, which is @Ignore'd
            this.type = it.type
        }
    }

    fun isSplit(): Boolean {
        return id == SPLIT_CATEGORY_ID
    }

    companion object {
        const val NO_CATEGORY_ID: Long = 0
        const val SPLIT_CATEGORY_ID: Long = -1

        fun noCategory(): Category {
            return Category().apply {
                this.id = NO_CATEGORY_ID // id from MyEntity
                this.left = 1 // left_node from CategoryEntity
                this.right = 2 // right_node from CategoryEntity
                this.title = "<NO_CATEGORY>" // title from MyEntity
                // parentId will be null by default
            }
        }

        fun splitCategory(): Category {
            return Category().apply {
                this.id = SPLIT_CATEGORY_ID
                this.left = 0
                this.right = 0
                this.title = "<SPLIT_CATEGORY>"
                // parentId will be null by default
            }
        }

        fun isSplit(categoryId: Long): Boolean {
            return SPLIT_CATEGORY_ID == categoryId
        }

        fun getTitle(title: String?, level: Int): String { // title can be nullable
            val span = getTitleSpan(level)
            return span + (title ?: "")
        }

        fun getTitleSpan(level: Int): String {
            var currentLevel = level - 1
            return when {
                currentLevel <= 0 -> ""
                currentLevel == 1 -> "-- "
                currentLevel == 2 -> "---- "
                currentLevel == 3 -> "------ "
                else -> {
                    val sb = StringBuilder()
                    for (i in 1 until currentLevel) {
                        sb.append("--")
                    }
                    if (currentLevel > 0) sb.append(" ") // Add space for levels > 3 too
                    sb.toString()
                }
            }
        }

        // fun formCursor(c: Cursor): Category { // Android specific, comment out
        //     // ...
        // }
    }
}
