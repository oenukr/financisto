// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.model

interface SortableEntity {
    fun getSortOrder(): Long
}
