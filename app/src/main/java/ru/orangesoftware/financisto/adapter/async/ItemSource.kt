package ru.orangesoftware.financisto.adapter.async

interface ItemSource<T> {
    fun clazz(): Class<T>
    fun getCount(): Int
    fun getItem(position: Int): T?
    fun close()
    fun setConstraint(constraint: CharSequence?)
}
