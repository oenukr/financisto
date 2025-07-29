package ru.orangesoftware.financisto.repository.model

data class Period(
    val type: ru.orangesoftware.financisto.repository.model.PeriodType,
    var start: Long,
    val end: Long
) {
    fun isSame(start: Long, end: Long): Boolean = this.start == start && this.end == end
    fun isCustom(): Boolean = type == ru.orangesoftware.financisto.repository.model.PeriodType.CUSTOM
}
