package com.jing.sakura.home

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
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
import com.jing.sakura.extend.observeLiveData
import com.jing.sakura.presenter.AnimeCardPresenter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BrowseSupportFragment() {


    private val backgroundManager by lazy {
        val activity = requireActivity()
        BackgroundManager.getInstance(activity).apply {
            attach(activity.window)
        }
    }

    private val viewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        headersState = HEADERS_DISABLED
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)

        if (savedInstanceState == null) {
            prepareEntranceTransition()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeHomePageData()
        setupItemClickListener()
//        setDynamicBackground()
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
        val adapter = ArrayObjectAdapter(ListRowPresenter())
        val cardWidth = resources.getDimension(R.dimen.poster_width).toInt()
        val cardHeight = resources.getDimension(R.dimen.poster_height).toInt()

        for (series in homePageData.seriesList) {
            val headerItem = HeaderItem(series.name)

            val rowAdapter = ArrayObjectAdapter(AnimeCardPresenter(cardWidth, cardHeight))
            for (anime in series.value) {
                rowAdapter.add(anime)
            }
            adapter.add(ListRow(headerItem, rowAdapter))
        }
        this.adapter = adapter
    }

    private fun observeHomePageData() {
        observeLiveData(viewModel.homePageData) { data ->
            when (data) {
                is Resource.Loading -> prepareEntranceTransition()
                is Resource.Success -> {
                    renderData(data.data)
                    startEntranceTransition()
                }
                else -> {}
            }
        }
    }


}