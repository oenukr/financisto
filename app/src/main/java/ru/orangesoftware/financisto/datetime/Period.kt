package ru.orangesoftware.financisto.datetime

data class Period(
    val type: PeriodType,
    var start: Long,
    val end: Long
) {
    fun isSame(start: Long, end: Long): Boolean = this.start == start && this.end == end
    fun isCustom(): Boolean = type == PeriodType.CUSTOM
}
