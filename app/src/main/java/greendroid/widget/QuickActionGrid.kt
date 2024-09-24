/*
 * Copyright (C) 2010 Cyril Mottier (https://www.cyrilmottier.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greendroid.widget

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView

import ru.orangesoftware.financisto.R

/**
 * A {@link QuickActionGrid} is an implementation of a {@link QuickActionWidget}
 * that displays {@link greendroid.widget.QuickAction}s in a grid manner. This is usually used to create
 * a shortcut to jump between different type of information on screen.
 * 
 * @author Benjamin Fellous
 * @author Cyril Mottier
 */
class QuickActionGrid(context: Context) : QuickActionWidget(context) {

    init {
        setContentView(R.layout.gd_quick_action_grid)
    }

    private val mGridView: GridView = contentView.findViewById(R.id.gdi_grid)

    fun setNumColumns(columns: Int) {
        mGridView.numColumns = columns
    }

    override fun populateQuickActions(quickActions: MutableList<QuickAction>) {
        mGridView.adapter = object: BaseAdapter() {
            override fun getView(position: Int, view: View?, parent: ViewGroup?): View =
                (view as? TextView ?: LayoutInflater.from(context)
                    .inflate(R.layout.gd_quick_action_grid_item, mGridView, false) as TextView)
                    .apply {
                        text = quickActions[position].title
                        setCompoundDrawablesWithIntrinsicBounds(
                            null,
                            quickActions[position].drawable,
                            null,
                            null
                        )
                    }

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getItem(position: Int): Any? = null

            override fun getCount(): Int = quickActions.size
        }
        mGridView.onItemClickListener = internalItemClickListener
    }

    override fun onMeasureAndLayout(anchorRect: Rect, contentView: View) {
        contentView.measure(
            MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val rootHeight = contentView.measuredHeight

        val offsetY = arrowOffsetY
        val dyTop = anchorRect.top
        val dyBottom = screenHeight - anchorRect.bottom

        val onTop = (dyTop > dyBottom)
        val popupY = if (onTop) anchorRect.top - rootHeight + offsetY else anchorRect.bottom - offsetY

        setWidgetSpecs(popupY, onTop)
    }

    private val internalItemClickListener: OnItemClickListener = OnItemClickListener { _, _, position, _ ->
        onQuickActionClickListener.onQuickActionClicked(this@QuickActionGrid, position)
        if (dismissOnClick) {
            dismiss()
        }
    }
}
