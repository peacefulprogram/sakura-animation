package com.jing.sakura.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.viewModels
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import coil.load
import com.jing.sakura.Constants
import com.jing.sakura.R
import com.jing.sakura.databinding.HistoryVideoCardLayoutBinding
import com.jing.sakura.room.VideoHistoryEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFragment :
    VerticalGridSupportFragment() {

    private val viewModel: HistoryViewModel by viewModels()

    private lateinit var noHistoryHintView: View

    private val historyDiff = object : DiffUtil.ItemCallback<VideoHistoryEntity>() {
        override fun areItemsTheSame(
            oldItem: VideoHistoryEntity,
            newItem: VideoHistoryEntity
        ): Boolean = oldItem.animeId == newItem.animeId

        override fun areContentsTheSame(
            oldItem: VideoHistoryEntity,
            newItem: VideoHistoryEntity
        ): Boolean = oldItem == newItem

    }


    private val pagingDataAdapter by lazy {
        PagingDataAdapter(
            diffCallback = historyDiff,
            presenter = VideoHistoryCardPresenter()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        title = "历史记录"
        gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM, false).apply {
            numberOfColumns = 5
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = pagingDataAdapter
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pager.collectLatest { pagingDataAdapter.submitData(it) }
            }
        }
    }

    private fun deleteAllHistory() {
        viewModel.deleteAllHistory {
            pagingDataAdapter.refresh()
        }
    }


    private inner class VideoHistoryCardPresenter : Presenter() {
        val mDefaultCardImage =
            ContextCompat.getColor(requireContext(), R.color.gray900).toDrawable()

        override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
            val vb = HistoryVideoCardLayoutBinding.inflate(LayoutInflater.from(parent!!.context))
            vb.root.tag = vb
            return ViewHolder(vb.root)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
            val video = item as VideoHistoryEntity
            with(viewHolder!!.view.tag as HistoryVideoCardLayoutBinding) {
                root.setOnClickListener {
                    findNavController().navigate(
                        HistoryFragmentDirections.actionHistoryFragmentToAnimeDetailFragment(
                            "${Constants.SAKURA_URL}/show/${video.animeId}.html"
                        )
                    )
                }
                root.setOnLongClickListener {
                    deleteAllHistory()
                    true
                }
                title.text = item.animeName
                subTitle.text = item.lastEpisodeName
                if (video.coverUrl.isEmpty()) {
                    cover.setImageDrawable(mDefaultCardImage)
                } else {
                    cover.load(video.coverUrl) {
                        error(mDefaultCardImage)
                    }
                }
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        }

    }

}