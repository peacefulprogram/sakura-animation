@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.common.ChangeSourceDialog
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.safelyRequestFocus
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
    var showChooseSourceDialog by remember {
        mutableStateOf(false)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val firstItemFocusRequester = remember {
            FocusRequester()
        }
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(containerWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            content = {
                item(span = { TvGridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "搜索结果", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = viewModel.sourceName,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                if (refreshState is LoadState.NotLoading && pagingItems.itemCount > 0) {
                    items(count = itemCount, key = pagingItems.itemKey { it.id }) { index ->
                        val video = pagingItems[index] ?: return@items
                        Box(
                            modifier = Modifier.size(containerWidth, containerHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            VideoCard(
                                modifier = Modifier
                                    .size(cardWidth, cardHeight)
                                    .run {
                                        if (index == 0) {
                                            focusRequester(firstItemFocusRequester)
                                        } else {
                                            this
                                        }
                                    },
                                imageUrl = video.imageUrl,
                                title = video.title,
                                subTitle = video.currentEpisode,
                                onLongClick = {
                                    showChooseSourceDialog = true
                                }
                            ) {
                                DetailActivity.startActivity(context, video.id, viewModel.sourceId)
                            }
                        }
                    }
                }
            })
        LaunchedEffect(refreshState) {
            if (refreshState is LoadState.NotLoading && itemCount > 0) {
                firstItemFocusRequester.safelyRequestFocus()
            }
        }

        if (refreshState == LoadState.Loading) {
            Loading()
        } else if (refreshState is LoadState.Error) {
            ErrorTip(message = refreshState.error.message ?: refreshState.error.toString()) {
                pagingItems.refresh()
            }
        } else if (itemCount == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "什么都没有哦", style = MaterialTheme.typography.bodyLarge)
                val focusRequester = remember {
                    FocusRequester()
                }
                Button(
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = {
                        showChooseSourceDialog = true
                    }) {
                    Text(text = "切换数据源")
                }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }
    }
    if (showChooseSourceDialog) {
        ChangeSourceDialog(
            allSources = viewModel.allSources,
            currentSourceId = viewModel.sourceId,
            onDismissRequest = { showChooseSourceDialog = false },
            onChangeSource = { sourceId ->
                showChooseSourceDialog = false
                viewModel.sourceId = sourceId
                pagingItems.refresh()
            }
        )
    }
}

