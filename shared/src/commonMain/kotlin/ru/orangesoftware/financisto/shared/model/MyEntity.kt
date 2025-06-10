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

// import static ru.orangesoftware.orb.EntityManager.DEF_ID_COL; // KMP compatible DB access will be different
// import static ru.orangesoftware.orb.EntityManager.DEF_TITLE_COL; // KMP compatible DB access will be different

// import androidx.annotation.NonNull; // Android specific

// import java.util.HashMap; // Fine for KMP
// import java.util.List; // Fine for KMP
// import java.util.Map; // Fine for KMP

// import javax.persistence.Column; // JPA
// import javax.persistence.Id; // JPA
// import javax.persistence.Transient; // JPA - use Kotlin's @Transient

// import ru.orangesoftware.financisto.utils.Utils; // This will need to be replaced or made KMP compatible

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "my_entities") // Example table name, adjust as needed
open class MyEntity : MultiChoiceItem { // Made open. MultiChoiceItem might need to be moved/shared too.

    @PrimaryKey(autoGenerate = true) // Using Room's PrimaryKey, assuming autoGenerate is desired for new entities
    @ColumnInfo(name = "id") // DEF_ID_COL replacement
    override var id: Long = 0L // Default to 0 for non-nullable PK, Room handles generation if autoGenerate=true

    @ColumnInfo(name = "title") // DEF_TITLE_COL replacement
    override var title: String? = null // Implementing MultiChoiceItem, made nullable to match typical entity patterns

    @ColumnInfo(name = "is_active")
    var isActive: Boolean = true

    @Ignore // Using Room's Ignore instead of @kotlin.jvm.Transient for Room-specific transient fields
    override var checked: Boolean = false // Implementing MultiChoiceItem

    // @NonNull // Android specific
    override fun toString(): String {
        return title ?: super.toString() // Fallback to default toString if title is null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        //getClass() not suitable for KMP if classes are in different modules / classloaders
        //if (other == null || javaClass != other.javaClass) return false
        if (other == null || other !is MyEntity) return false


        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        fun splitIds(s: String?): LongArray? {
            // if (Utils.isEmpty(s)) { // Replace Utils.isEmpty
            if (s.isNullOrEmpty()) {
                return null
            }
            val a = s.split(",").toTypedArray()
            val count = a.size
            val ids = LongArray(count)
            for (i in 0 until count) {
                ids[i] = a[i].toLong()
            }
            return ids
        }

        fun <T : MyEntity> asMap(list: List<T>): Map<Long, T> {
            val map = HashMap<Long, T>()
            for (e in list) {
                map[e.id] = e
            }
            return map
        }

        fun indexOf(entities: List<MyEntity>?, id: Long): Int {
            if (entities != null) {
                val count = entities.size
                for (i in 0 until count) {
                    if (entities[i].id == id) {
                        return i
                    }
                }
            }
            return -1
        }

        fun <T : MyEntity> find(entities: List<T>?, id: Long): T? {
            entities?.forEach { e ->
                if (e.id == id) {
                    return e
                }
            }
            return null
        }
    }
}

// Interface for MultiChoiceItem - assuming it's simple like this
// If it's more complex, it needs to be moved and properly refactored.
interface MultiChoiceItem { // This might need to be an @Entity too or have its fields in MyEntity directly if persisted
    var id: Long // Consider if MultiChoiceItem itself should be an entity or if MyEntity just implements its fields
    var title: String?
    @get:Ignore // Room should ignore 'checked' from MultiChoiceItem if it's not a persisted field
    @set:Ignore
    var checked: Boolean
}
