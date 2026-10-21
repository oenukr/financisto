// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

interface RefreshSupportedActivity {
	fun recreateCursor()
    fun integrityCheck()
}
