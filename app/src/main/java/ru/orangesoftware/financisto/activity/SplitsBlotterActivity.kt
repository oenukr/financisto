package ru.orangesoftware.financisto.activity

import android.database.Cursor
import android.os.Bundle
import android.widget.ListAdapter
import androidx.core.view.isVisible
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.blotter.BlotterTotalCalculationTask
import ru.orangesoftware.financisto.blotter.TotalCalculationTask

class SplitsBlotterActivity : BlotterActivity() {

	override fun internalOnCreate(savedInstanceState: Bundle?) {
		super.internalOnCreate(savedInstanceState)
		bFilter.isVisible = false
	}

	override fun createCursor(): Cursor = db.getBlotterForAccountWithSplits(blotterFilter)

	override fun createAdapter(cursor: Cursor?): ListAdapter =
		TransactionsListAdapter(this, db, cursor)

	override fun createTotalCalculationTask(): TotalCalculationTask =
		BlotterTotalCalculationTask(this, db, blotterFilter, totalText)
}
