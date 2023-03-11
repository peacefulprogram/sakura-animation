package com.jing.sakura.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.viewModels
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.navigation.fragment.findNavController
import coil.load
import com.jing.sakura.R
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.databinding.DetailInfomationLayoutBinding
import com.jing.sakura.extend.observeLiveData
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.extend.showLongToast
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.presenter.AnimeCardPresenter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnimeDetailFragment : RowsSupportFragment() {

    private val viewModel by viewModels<DetailPageViewModel>()

    private lateinit var detailPageUrl: String
    private var animeName: String? = null

    private val progressBarManager = ProgressBarManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailPageUrl = AnimeDetailFragmentArgs.fromBundle(requireArguments()).detailUrl
        viewModel.loadData(detailPageUrl)
        onItemViewClickedListener = OnItemViewClickedListener { vh, item, _, _ ->
            when (item) {
                is AnimePlayListEpisode -> {
                    val viewHolder = vh as EpisodeViewHolder
                    val animeDetail =
                        (viewModel.detailPageData.value as Resource.Success<AnimeDetailPageData>).data
                    val arg = NavigateToPlayerArg(
                        animeName = animeName ?: "",
                        playIndex = viewHolder.episodeIndex,
                        playlist = viewHolder.playlist,
                        animeId = animeDetail.animeId,
                        coverUrl = animeDetail.imageUrl
                    )
                    findNavController().navigate(
                        AnimeDetailFragmentDirections.actionAnimeDetailFragmentToAnimePlayerFragment(
                            arg
                        )
                    )
                }
                is AnimeData -> {
                    findNavController().navigate(
                        AnimeDetailFragmentDirections.actionAnimeDetailFragmentSelf(
                            item.url
                        )
                    )
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val frameLayout = FrameLayout(requireContext())
        val view = super.onCreateView(inflater, container, savedInstanceState)
        frameLayout.addView(view)
        progressBarManager.setRootView(frameLayout)
        progressBarManager.initialDelay = 0
        progressBarManager.enableProgressBar()
        return frameLayout;
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBarManager.show()
        observeLiveData(viewModel.detailPageData) {
            when (it) {
                is Resource.Success -> {
                    progressBarManager.hide()
                    animeName = it.data.animeName
                    renderData(it.data)
                }
                is Resource.Loading -> progressBarManager.show()
                is Resource.Error -> {
                    requireContext().showLongToast("请求数据出错:${it.message}")
                    progressBarManager.hide()
                }
                else -> {}
            }
        }
    }

    private fun renderData(detailPageData: AnimeDetailPageData) {
        val rowsAdapter = createRowsAdapter()

        rowsAdapter.add(detailPageData)
        var foundLastEpisode = detailPageData.videoHistory == null
        detailPageData.playLists.forEachIndexed { index, playlist ->
            var lastPlayEpisodeIndex = -1
            val episodeAdapter = ArrayObjectAdapter(DetailEpisodePresenter(playlist))
            playlist.episodeList.forEachIndexed { index, episode ->
                if (!foundLastEpisode &&
                    detailPageData.videoHistory != null
                    && episode.episodeId == detailPageData.videoHistory!!.episodeId
                ) {
                    lastPlayEpisodeIndex = index
                    foundLastEpisode = true
                }
                episodeAdapter.add(episode)
            }
            val listRow = WithDefaultSelectIndexListRow(
                HeaderItem("播放列表${index + 1}"),
                episodeAdapter,
                lastPlayEpisodeIndex
            )
            rowsAdapter.add(listRow)
        }
        val cardWidth = requireContext().resources.getDimension(R.dimen.poster_width).toInt()
        val cardHeight = requireContext().resources.getDimension(R.dimen.poster_height).toInt()
        val relatedVideoAdapter =
            ArrayObjectAdapter(AnimeCardPresenter(cardWidth, cardHeight)).apply {
                detailPageData.otherAnimeList.forEach(this::add)
            }

        rowsAdapter.add(RelatedVideoListRow(HeaderItem("相关推荐"), relatedVideoAdapter))


        adapter = rowsAdapter

    }


    private fun createRowsAdapter(): ArrayObjectAdapter {
        val rowPresenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(
                AnimeDetailPageData::class.java,
                DetailInfoRowPresenter().apply {
                    this.selectEffectEnabled = false
                }
            )
            addClassPresenter(
                WithDefaultSelectIndexListRow::class.java,
                WithDefaultSelectIndexListRowPresenter().apply {
                    selectEffectEnabled = false
                })
            addClassPresenter(RelatedVideoListRow::class.java, ListRowPresenter().apply {
                selectEffectEnabled = false
            })
        }

        return ArrayObjectAdapter(rowPresenterSelector)
    }
}

class RelatedVideoListRow(headerItem: HeaderItem, adapter: ObjectAdapter) :
    ListRow(headerItem, adapter)

class WithDefaultSelectIndexListRow(
    headerItem: HeaderItem,
    adapter: ObjectAdapter,
    val selectIndex: Int = -1
) :
    ListRow(headerItem, adapter)

class WithDefaultSelectIndexListRowPresenter : ListRowPresenter() {

    private var mGridView: BaseGridView? = null

    override fun createRowViewHolder(parent: ViewGroup?): RowPresenter.ViewHolder {
        val vh = super.createRowViewHolder(parent) as ListRowPresenter.ViewHolder
        mGridView = vh.gridView
        return vh
    }

    override fun onBindViewHolder(
        viewHolder: Presenter.ViewHolder?,
        item: Any?,
        payloads: MutableList<Any>?
    ) {
        super.onBindViewHolder(viewHolder, item, payloads)
        val row = item as WithDefaultSelectIndexListRow
        if (row.selectIndex >= 0) {
            mGridView?.selectedPosition = row.selectIndex
        }
    }
}

class DetailInfoRowPresenter : RowPresenter() {


    init {
        headerPresenter = null
    }

    override fun createRowViewHolder(parent: ViewGroup?): ViewHolder {
        val ctx = parent!!.context
        val binding = DetailInfomationLayoutBinding.inflate(LayoutInflater.from(ctx))
        binding.root.tag = binding
        return ViewHolder(binding.root)
    }

    override fun onBindRowViewHolder(vh: ViewHolder?, item: Any?) {
        vh!!
        val detail = item as AnimeDetailPageData
        with(vh.view.tag as DetailInfomationLayoutBinding) {
            if (detail.videoHistory != null) {
                val lastPlayInfo = StringBuilder("上次播放到")
                with(detail.videoHistory!!) {
                    lastPlayInfo.append(lastEpisodeName)
                    lastPlayInfo.append(' ')
                    lastPlayInfo.append((lastPlayTime / 1000).secondsToMinuteAndSecondText())
                    if (videoDuration > 0) {
                        lastPlayInfo.append('/')
                        lastPlayInfo.append((videoDuration / 1000).secondsToMinuteAndSecondText())
                    }
                }
                lastPlayEpisode.text = lastPlayInfo.toString()
            }
            detailAlias.text = detail.animeAlias
            detailName.text = detail.animeName
            detailCoverImg.load(detail.imageUrl)
            detailRegion.text = detail.region.name
            detailRelease.text = detail.releaseDay
            detailTags.text = detail.tags.joinToString("  ") { it.name }
            detailCurrentEpisode.text = detail.latestEpisode
            detailDesc.text = detail.description
        }
    }

}
