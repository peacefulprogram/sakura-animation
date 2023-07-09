package com.jing.sakura.home

import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ListRowView
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.SearchOrbView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jing.sakura.R
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.extend.showShortToast
import com.jing.sakura.history.HistoryActivity
import com.jing.sakura.presenter.AnimeCardPresenter
import com.jing.sakura.search.SearchActivity
import com.jing.sakura.timeline.UpdateTimelineActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class HomeFragment : BrowseSupportFragment() {

    private val backgroundManager by lazy {
        val activity = requireActivity()
        BackgroundManager.getInstance(activity).apply {
            attach(activity.window)
        }
    }

    private val rowAdapterCache = mutableMapOf<String, ArrayObjectAdapter>()

    private val fragmentAdapter = ArrayObjectAdapter(object : ListRowPresenter() {
        override fun createRowViewHolder(parent: ViewGroup?): RowPresenter.ViewHolder {
            return super.createRowViewHolder(parent).apply {
                (view as ListRowView).gridView.setItemSpacing(20)
            }
        }
    })

    private lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        headersState = HEADERS_DISABLED
        viewModel = get()
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)

        if (savedInstanceState == null) {
            prepareEntranceTransition()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)!!
        val iconRow = root.findViewById<HorizontalGridView>(R.id.icon_row)
        iconRow.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                outRect.right =
                    requireContext().resources.getDimension(R.dimen.home_icon_gap).toInt()
            }
        })
        iconRow.adapter =
            IconRowAdapter(listOf(TopIcon(icon = R.drawable.search_icon, color = R.color.green400) {
                SearchActivity.startActivity(requireContext())
            }, TopIcon(icon = R.drawable.history_icon, color = R.color.yellow500) {
                HistoryActivity.startActivity(requireContext())
            }, TopIcon(icon = R.drawable.timeline_icon, color = R.color.cyan500) {
                UpdateTimelineActivity.startActivity(requireContext())
            }))
        return root;
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                viewModel.loadData()
                true
            } else {
                false
            }
        }
        observeHomePageData()
        setupItemClickListener()
        this.adapter = fragmentAdapter
//        setDynamicBackground()
    }


    override fun onStart() {
        super.onStart()
        viewModel.loadData()
    }


    private fun setupItemClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            with(item as AnimeData) {
                DetailActivity.startActivity(requireContext(), url)
            }

        }
    }

    private fun setDynamicBackground() {
        setOnItemViewSelectedListener { itemViewHolder, _, _, _ ->
            if (itemViewHolder?.view != null) {
                val bitmapDrawable =
                    (itemViewHolder.view as ImageCardView).mainImageView.drawable as? BitmapDrawable
                if (bitmapDrawable != null) {
                    Palette.from(bitmapDrawable.bitmap).generate { palette ->
                        // Priority for vibrantSwatch, if not available dominantSwatch
                        (palette?.vibrantSwatch ?: palette?.dominantSwatch)?.let { swatch ->
                            backgroundManager.color = swatch.rgb
                        }
                    }
                }
            }
        }
    }

    private fun renderData(homePageData: HomePageData) {
        val cardWidth = resources.getDimension(R.dimen.poster_width).toInt()
        val cardHeight = resources.getDimension(R.dimen.poster_height).toInt()

        val rows = homePageData.seriesList.map { series ->
            val headerItem = HeaderItem(series.name)
            val rowAdapter = if (rowAdapterCache.containsKey(series.name)) {
                rowAdapterCache[series.name]!!
            } else {
                ArrayObjectAdapter(
                    AnimeCardPresenter(
                        cardWidth, cardHeight
                    )
                ).apply {
                    rowAdapterCache[series.name] = this
                }
            }
            rowAdapter.setItems(series.value, diffCallback)
            ListRow(headerItem, rowAdapter)
        }

        fragmentAdapter.setItems(rows, object : DiffCallback<ListRow>() {
            override fun areItemsTheSame(oldItem: ListRow, newItem: ListRow): Boolean =
                oldItem.adapter == newItem.adapter && oldItem.headerItem.name == newItem.headerItem.name

            override fun areContentsTheSame(oldItem: ListRow, newItem: ListRow): Boolean =
                areItemsTheSame(oldItem, newItem)

        })
    }

    private val diffCallback = object : DiffCallback<AnimeData>() {
        override fun areContentsTheSame(oldItem: AnimeData, newItem: AnimeData): Boolean {
            return oldItem.url == newItem.url && oldItem.currentEpisode == newItem.currentEpisode
        }

        override fun areItemsTheSame(oldItem: AnimeData, newItem: AnimeData): Boolean =
            oldItem.url == newItem.url
    }

    private fun observeHomePageData() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homePageData.collectLatest { data ->
                    when (data) {
                        is Resource.Success -> {
                            renderData(data.data)
                            startEntranceTransition()
                        }

                        is Resource.Error -> {
                            requireContext().showShortToast("请求数据失败:${data.message}")
                            startEntranceTransition()
                        }

                        else -> {}
                    }

                }
            }
        }
    }

    class IconRowAdapter(private val iconList: List<TopIcon>) : Adapter<OrbViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrbViewHolder {
            val view = SearchOrbView(parent.context, null)
            return OrbViewHolder(view)
        }

        override fun getItemCount(): Int = iconList.size

        override fun onBindViewHolder(holder: OrbViewHolder, position: Int) {
            with(iconList[position]) {
                with(holder.searchOrbView) {
                    orbIcon = ContextCompat.getDrawable(context, icon)
                    orbColor = ContextCompat.getColor(context, color)
                    setOnOrbClickedListener { onClick() }
                }
            }
        }
    }

    class OrbViewHolder(val searchOrbView: SearchOrbView) : ViewHolder(searchOrbView)

    data class TopIcon(
        @DrawableRes val icon: Int, @ColorRes val color: Int, val onClick: () -> Unit
    )

}