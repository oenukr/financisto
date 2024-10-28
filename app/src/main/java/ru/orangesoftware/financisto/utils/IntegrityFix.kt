package ru.orangesoftware.financisto.utils

import ru.orangesoftware.financisto.db.DatabaseAdapter

class IntegrityFix(private val db: DatabaseAdapter) {

    fun fix() {
        db.restoreSystemEntities()
        db.recalculateAccountsBalances()
        db.rebuildRunningBalances()
    }

}
