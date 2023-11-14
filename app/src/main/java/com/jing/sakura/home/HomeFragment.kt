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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.theme.SakuraTheme
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
                SearchActivity.startActivity(requireContext(), viewModel.currentSourceId)
            }, TopIcon(icon = R.drawable.history_icon, color = R.color.yellow500) {
                HistoryActivity.startActivity(requireContext())
            }, TopIcon(icon = R.drawable.timeline_icon, color = R.color.cyan500) {
                UpdateTimelineActivity.startActivity(requireContext(), viewModel.currentSourceId)
            }, TopIcon(icon = R.drawable.switch_icon, color = R.color.blue400) {
                changeSource()
            }))
        return root;
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                viewModel.loadData(false)
                true
            } else {
                false
            }
        }
        observeHomePageData()
        setupItemClickListener()
        this.adapter = fragmentAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentSource.collectLatest { title = it.name }
            }
        }
//        setDynamicBackground()
    }


    override fun onStart() {
        super.onStart()
        viewModel.loadData(true)
    }


    private fun setupItemClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            with(item as AnimeData) {
                DetailActivity.startActivity(requireContext(), id, sourceId)
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
                            progressBarManager.hide()
                            renderData(data.data)
                        }

                        is Resource.Error -> {
                            progressBarManager.hide()
                            requireContext().showShortToast("请求数据失败:${data.message}")
                        }

                        is Resource.Loading -> {
                            if (!data.silent) {
                                progressBarManager.show()
                            }
                        }
                    }

                }
            }
        }
    }

    fun changeSource() {
        val fragmentManager = requireActivity().supportFragmentManager
        ChangeSourceDialog(
            allSources = viewModel.getAllSources(),
            currentSourceId = viewModel.currentSourceId,
        ) {
            rowAdapterCache.clear()
            viewModel.changeSource(it.first)
        }.apply {
            showNow(fragmentManager, "")
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


    class ChangeSourceDialog(
        private val allSources: List<Pair<String, String>>,
        private val currentSourceId: String,
        private val onSourceClick: (source: Pair<String, String>) -> Unit
    ) : DialogFragment() {

        @OptIn(ExperimentalTvMaterial3Api::class)
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val defaultIndex = allSources.indexOfFirst { it.first == currentSourceId }
            return ComposeView(requireContext()).apply {
                setContent {
                    SakuraTheme {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(
                                    dimensionResource(id = R.dimen.screen_h_padding),
                                    dimensionResource(id = R.dimen.screen_v_padding)
                                )
                                .fillMaxSize()
                        ) {
                            CompositionLocalProvider(
                                androidx.tv.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                            ) {

                                val listState =
                                    rememberTvLazyListState(initialFirstVisibleItemIndex = defaultIndex)
                                val focusRequester = remember {
                                    FocusRequester()
                                }
                                Column {
                                    Text(text = stringResource(R.string.choose_animation_source))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TvLazyColumn(state = listState, content = {
                                        items(count = allSources.size) { sourceIndex ->
                                            val source = allSources[sourceIndex]
                                            val modifier = Modifier.run {
                                                if (sourceIndex == defaultIndex) {
                                                    focusRequester(focusRequester)
                                                } else {
                                                    this
                                                }
                                            }
                                            SourceItem(source = source, modifier = modifier)
                                        }
//                            items(allSources, key = { it.first }) { source ->
//                                SourceItem(source = source)
//                            }
                                    })
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }
                                }
                            }

                        }
                    }
                }
            }

        }

        @OptIn(ExperimentalTvMaterial3Api::class)
        @Composable
        fun SourceItem(modifier: Modifier = Modifier, source: Pair<String, String>) {
            var focused by remember {
                mutableStateOf(false)
            }
            val color =
                if (currentSourceId == source.first) colorResource(id = R.color.cyan300) else MaterialTheme.colorScheme.onSurface
            Text(text = source.second,
                color = color,
                modifier = modifier
                    .fillMaxWidth()
//                    .background(colorResource(id = R.color.red300))
                    .background(if (focused) colorResource(id = R.color.red300) else Color.Transparent)
                    .padding(10.dp)
                    .onFocusChanged {
                        focused = it.isFocused || it.hasFocus
                    }
                    .focusable()
                    .clickable {
                        onSourceClick(source)
                        dismissNow()
                    }
            )

        }

    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Preview
@Composable
fun DemoPreview() {
    SakuraTheme {
        Box(modifier = Modifier.size(200.dp, 600.dp)) {
            Text(
                text = "test1", modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(id = R.color.red300))
                    .padding(10.dp)
            )
        }

    }
}