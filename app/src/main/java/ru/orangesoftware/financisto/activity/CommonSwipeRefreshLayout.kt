package ru.orangesoftware.financisto.activity

import android.content.Context
import android.util.AttributeSet
import android.view.View

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class CommonSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(
    context,
    attrs,
) {
    private var mScrollingView: View? = null

    override fun canChildScrollUp(): Boolean {
        return mScrollingView != null && mScrollingView!!.canScrollVertically(-1)
    }

    fun setScrollingView(scrollingView: View) {
        mScrollingView = scrollingView;
    }
}
