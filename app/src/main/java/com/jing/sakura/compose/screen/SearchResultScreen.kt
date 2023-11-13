@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.search.SearchResultViewModel

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    val context = LocalContext.current
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    val gridState = rememberTvLazyGridState()
    val cardWidth = dimensionResource(id = R.dimen.poster_width)
    val cardHeight = dimensionResource(id = R.dimen.poster_height)
    val scale = 1.1f
    val containerWidth = cardWidth * scale
    val containerHeight = cardHeight * scale
    val itemCount = pagingItems.itemCount
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val firstItemFocusRequester = remember {
            FocusRequester()
        }
        TvLazyVerticalGrid(columns = TvGridCells.Adaptive(containerWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            content = {
                item(span = { TvGridItemSpan(maxLineSpan) }) {
                    Text(text = "搜索结果", style = MaterialTheme.typography.headlineMedium)
                }
                items(count = itemCount, key = { pagingItems[it]?.url ?: it }) { index ->
                    val video = pagingItems[index] ?: return@items
                    Box(
                        modifier = Modifier.size(containerWidth, containerHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoCard(
                            modifier = Modifier.size(cardWidth, cardHeight).run {
                                if (index == 0) {
                                    focusRequester(firstItemFocusRequester)
                                } else {
                                    this
                                }
                            },
                            imageUrl = video.imageUrl,
                            title = video.title
                        ) {
                            DetailActivity.startActivity(context, video.id, viewModel.sourceId)
                        }
                    }
                }
            })
        LaunchedEffect(refreshState) {
            try {
                if (refreshState is LoadState.NotLoading && itemCount > 0) {
                    firstItemFocusRequester.requestFocus()
                }
            } catch (_: Exception) {
            }
        }

        if (refreshState == LoadState.Loading) {
            Loading()
        } else if (refreshState is LoadState.Error) {
            ErrorTip(message = refreshState.error.message ?: refreshState.error.toString())
        } else if (itemCount == 0) {
            Text(text = "什么都没有哦", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

