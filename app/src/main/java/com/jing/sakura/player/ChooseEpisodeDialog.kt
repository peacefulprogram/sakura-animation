package com.jing.sakura.player

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.*
import com.jing.sakura.R
import com.jing.sakura.databinding.ChooseItemIndicatorLayoutBinding
import com.jing.sakura.extend.dpToPixels
import com.jing.sakura.extend.getColorWithAlpha

class ChooseEpisodeDialog<T>(
    private val dataList: List<T>,
    private val defaultSelectIndex: Int,
    private val viewWidth: Int,
    private val getText: (position: Int, item: T) -> String,
    private val onChoose: (position: Int, item: T) -> Unit
) : DialogFragment() {

    private lateinit var recyclerView: RecyclerView

    var dismissListener: () -> Unit = {}

    override fun onStart() {
        super.onStart()
        dialog?.window?.attributes?.run {
            height = WindowManager.LayoutParams.MATCH_PARENT
            width = viewWidth.dpToPixels(requireContext()).toInt()
            gravity = Gravity.END
            dialog!!.window!!.attributes = this
        }
        dialog?.window?.attributes?.dimAmount = 0f
        dialog!!.window!!.setBackgroundDrawable(
            requireContext().getColorWithAlpha(
                R.color.zinc900,
                0.3f
            ).toDrawable()
        )
        if (defaultSelectIndex >= 0 && defaultSelectIndex < dataList.size) {
            recyclerView.smoothScrollToPosition(defaultSelectIndex)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
        }
        recyclerView.adapter = object : Adapter<ChooseItemViewHolder>() {

            private var init = false

            private val chosenColor = ContextCompat.getColor(requireContext(), R.color.cyan300)
            private val blurColor = ContextCompat.getColor(requireContext(), R.color.gray200)
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): ChooseItemViewHolder {
                return ChooseItemViewHolder(
                    ChooseItemIndicatorLayoutBinding.inflate(
                        LayoutInflater.from(
                            parent.context
                        ), parent, false
                    )
                )
            }

            override fun onBindViewHolder(holder: ChooseItemViewHolder, position: Int) {
                with(holder.viewBinding) {
                    textContainer.text = getText(position, dataList[position])
                    if (defaultSelectIndex == position) {
                        textContainer.setTextColor(chosenColor)
                    } else {
                        textContainer.setTextColor(blurColor)
                    }
                }

                with(holder.viewBinding.root) {
                    setOnClickListener {
                        onChoose(position, dataList[position])
                        dismissNow()
                    }
                    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                        holder.viewBinding.root.background = if (hasFocus) {
                            requireContext().getColorWithAlpha(
                                R.color.gray600,
                                0.4f
                            ).toDrawable()
                        } else {
                            ColorDrawable(Color.TRANSPARENT)
                        }
                        with(holder.viewBinding.indicator) {
                            background = ColorDrawable(chosenColor)
                            visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                        }
                    }
                }
            }

            override fun getItemCount(): Int {
                return dataList.size
            }

            override fun onBindViewHolder(
                holder: ChooseItemViewHolder,
                position: Int,
                payloads: MutableList<Any>
            ) {
                super.onBindViewHolder(holder, position, payloads)
                if (!init && position == defaultSelectIndex) {
                    holder.viewBinding.root.requestFocus()
                    init = true
                }
            }

        }
        recyclerView.addItemDecoration(object : ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.top = 10
                outRect.bottom = 10
            }
        })
        return recyclerView
    }


    private class ChooseItemViewHolder(val viewBinding: ChooseItemIndicatorLayoutBinding) :
        ViewHolder(viewBinding.root)


    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        dismissListener()
    }

}