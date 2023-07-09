package com.jing.sakura.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class RestoreFocusRecyclerView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    RecyclerView(context, attrs, defStyleAttr) {


    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null)

    private var mLastFocusedPosition = 0

    init {
        this.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        this.isFocusable = true
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (child != null) {
            mLastFocusedPosition = getChildViewHolder(child).absoluteAdapterPosition
        }
    }

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (hasFocus()) {
            super.addFocusables(views, direction, focusableMode)
        } else {
            layoutManager?.findViewByPosition(mLastFocusedPosition)?.let {
                views.add(it)
            }
        }
    }
}