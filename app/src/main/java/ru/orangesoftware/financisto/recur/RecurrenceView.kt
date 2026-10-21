// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.recur

import android.widget.LinearLayout

interface RecurrenceView {
	fun createNodes(layout: LinearLayout)
	fun stateToString(): String
	fun stateFromString(state: String)
	fun validateState(): Boolean
}
