// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.utils

interface ExecutableEntityEnum<V> : EntityEnum {
    fun execute(value: V)
}
