package com.jing.sakura.compose.screen

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.Constants
import com.jing.sakura.R
import com.jing.sakura.compose.common.ConfirmDeleteDialog
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.launch

@Composable
fun VideoHistoryScreen(viewModel: HistoryViewModel) {

    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    if (refreshState == LoadState.Loading) {
        Loading()
        return
    }
    if (refreshState is LoadState.Error) {
        ErrorTip(message = refreshState.error.message ?: refreshState.error.toString()) {
            pagingItems.refresh()
        }
        return
    }


    val containerWidth = dimensionResource(id = R.dimen.history_poster_width) * 1.1f
    val containerHeight = dimensionResource(id = R.dimen.history_poster_height) * 1.1f
    val context = LocalContext.current
    val gridState = rememberTvLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var confirmRemoveVideo by remember {
        mutableStateOf<VideoHistoryEntity?>(null)
    }
    val firstVideoFocusRequester = remember {
        FocusRequester()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(containerWidth),
            modifier = Modifier
                .fillMaxSize(),
            state = gridState,
            content = {
                item(span = { TvGridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = stringResource(R.string.playback_history),
                            style = MaterialTheme.typography.titleLarge,
                        )

                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = stringResource(R.string.click_ok_del_tip),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                items(count = pagingItems.itemCount) { videoIndex ->
                    val video = pagingItems[videoIndex]!!
                    Box(
                        modifier = Modifier.size(containerWidth, containerHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoCard(
                            modifier = Modifier.size(
                                dimensionResource(id = R.dimen.history_poster_width),
                                dimensionResource(id = R.dimen.history_poster_height)
                            ).run {
                                if (videoIndex == 0) {
                                    focusRequester(firstVideoFocusRequester)
                                } else {
                                    this
                                }
                            },
                            imageUrl = video.coverUrl,
                            title = video.animeName,
                            subTitle = video.lastEpisodeName,
                            onLongClick = {
                                confirmRemoveVideo = video
                            },
                            onKeyEvent = { keyEvent ->
                                if (keyEvent.key == Key.Menu && keyEvent.type == KeyEventType.KeyUp) {
                                    coroutineScope.launch {
                                        gridState.scrollToItem(0)
                                        pagingItems.refresh()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        ) {
                            DetailActivity.startActivity(
                                context,
                                "${Constants.SAKURA_URL}/show/${video.animeId}.html"
                            )

                        }
                    }
                }
            }
        )

        if (pagingItems.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.grid_no_data_tip),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    LaunchedEffect(refreshState) {
        try {
            if (refreshState is LoadState.NotLoading && pagingItems.itemCount > 0) {
                firstVideoFocusRequester.requestFocus()
            }
        } catch (e: Exception) {
            Log.w("PlayHistoryScreen", "request focus error: ${e.message}", e)
        }
    }

    val removeVideo = confirmRemoveVideo ?: return
    ConfirmDeleteDialog(
        text = String.format(
            stringResource(id = R.string.confirm_delete_template),
            removeVideo.animeName
        ),
        onDeleteClick = {
            confirmRemoveVideo = null
            coroutineScope.launch {
                gridState.scrollToItem(0)
                firstVideoFocusRequester.requestFocus()
            }
            viewModel.deleteHistoryByAnimeId(removeVideo.animeId)
        },
        onDeleteAllClick = {
            confirmRemoveVideo = null
            coroutineScope.launch {
                gridState.scrollToItem(0)
                firstVideoFocusRequester.requestFocus()
            }
        },
        onCancel = {
            confirmRemoveVideo = null
        }
    )

}