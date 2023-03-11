package com.jing.sakura.home

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout.LayoutParams
import androidx.fragment.app.viewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import com.jing.sakura.R
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.dpToPixels
import com.jing.sakura.extend.observeLiveData
import com.jing.sakura.extend.showShortToast
import com.jing.sakura.presenter.AnimeCardPresenter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BrowseSupportFragment() {

    private lateinit var historyIconView: View


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

    private val viewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        headersState = HEADERS_DISABLED
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)

        if (savedInstanceState == null) {
            prepareEntranceTransition()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        historyIconView = layoutInflater.inflate(R.layout.history_icon_layout, container, false)
        LayoutParams(historyIconView.layoutParams.width, historyIconView.layoutParams.height)
            .apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = (52 * 1.3).dpToPixels(requireActivity()).toInt()
                historyIconView.layoutParams = this
            }
        val titleView = titleView as TitleView
        historyIconView.setOnFocusChangeListener { _, hasFocus ->
            val zoom = if (hasFocus) 1.2f else 1f
            historyIconView.animate().scaleX(zoom).scaleY(zoom).setDuration(200L).start()
        }
        historyIconView.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToHistoryFragment())
        }
        titleView.searchAffordanceView.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                historyIconView.requestFocus()
                true
            } else {
                false
            }
        }
        titleView.addView(historyIconView)
        return root;
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnKeyListener { _, keyCode, _ ->
            println(keyCode)
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                viewModel.loadData()
                true
            } else {
                false
            }
        }
        observeHomePageData()
        setupItemClickListener()
        setOnSearchClickedListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSearchFragment())
        }
        this.adapter = fragmentAdapter
//        setDynamicBackground()
    }


    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }


    private fun setupItemClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            with(item as AnimeData) {
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToAnimeDetailFragment(
                        url
                    )
                )
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
            val rowAdapter =
                rowAdapterCache.computeIfAbsent(series.name) {
                    ArrayObjectAdapter(
                        AnimeCardPresenter(
                            cardWidth,
                            cardHeight
                        )
                    )
                }.apply {
                    setItems(series.value, diffCallback)
                }
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
        observeLiveData(viewModel.homePageData) { data ->
            when (data) {
//                is Resource.Loading -> prepareEntranceTransition()
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