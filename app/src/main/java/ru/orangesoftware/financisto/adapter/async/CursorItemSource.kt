package ru.orangesoftware.financisto.adapter.async

import android.database.Cursor

abstract class CursorItemSource<T> : ItemSource<T>, AutoCloseable {
    protected var cursor: Cursor? = null

    override fun getCount(): Int {
        prepareCursor()
        return cursor?.count ?: 0
    }

    override fun getItem(position: Int): T? {
        prepareCursor()
        if(cursor?.moveToPosition(position) == true){
            return loadItem()
        }
        return itemOnError()
    }

    fun prepareCursor() {
        if (cursor == null || cursor?.isClosed == true) {
            cursor = initCursor()
        }
    }

    protected fun itemOnError(): T? {
        return null
    }

    protected abstract fun loadItem(): T

    abstract fun initCursor(): Cursor

    override fun close() {
        cursor?.close()
    }
}
