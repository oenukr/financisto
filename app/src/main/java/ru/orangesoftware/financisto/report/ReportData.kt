package ru.orangesoftware.financisto.report

import ru.orangesoftware.financisto.graph.GraphUnit
import ru.orangesoftware.financisto.model.Total

data class ReportData(
    val units: List<GraphUnit>,
    val total: Total,
)
