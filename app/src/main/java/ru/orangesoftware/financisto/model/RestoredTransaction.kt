package ru.orangesoftware.financisto.model

import java.util.Date

data class RestoredTransaction(
	val transactionId: Long,
	val dateTime: Date,
)
