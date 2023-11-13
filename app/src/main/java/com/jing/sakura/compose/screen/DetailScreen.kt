@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.CompactCard
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.PlaybackActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: DetailPageViewModel) {
    val videoDetailResource = viewModel.detailPageData.collectAsState().value
    if (videoDetailResource == Resource.Loading) {
        Loading()
        return
    }
    if (videoDetailResource is Resource.Error) {
        ErrorTip(message = videoDetailResource.message) {
            viewModel.loadData()
        }
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.fetchHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val videoDetail = (videoDetailResource as Resource.Success).data
    val context = LocalContext.current
    var reverseEpisode by remember {
        mutableStateOf(false)
    }

    val playlists = remember(reverseEpisode) {
        if (reverseEpisode) {
            videoDetail.playLists.map { it.copy(episodeList = it.episodeList.reversed()) }
        } else {
            videoDetail.playLists
        }
    }
    var lastPlayEpisodeId by remember {
        mutableStateOf("")
    }
    LaunchedEffect(Unit) {
        viewModel.latestProgress.collectLatest {
            if (it is Resource.Success) {
                if (lastPlayEpisodeId != it.data.episodeId) {
                    lastPlayEpisodeId = it.data.episodeId
                }
            }
        }
    }
    TvLazyColumn(
        modifier = Modifier.fillMaxSize(), content = {
            item {
                VideoInfoRow(videoDetail = videoDetail, viewModel = viewModel)
            }
            items(count = playlists.size, key = { playlists[it].name }) { playlistIndex ->
                val playlist = playlists[playlistIndex]
                val listState = rememberTvLazyListState()
                PlayListRow(episodes = playlist.episodeList,
                    lastPlayEpisodeId = lastPlayEpisodeId,
                    listState = listState,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlist.name, style = MaterialTheme.typography.titleMedium
                            )
                            if (playlistIndex == 0) {
                                Text(text = " | ")
                                Surface(
                                    onClick = {
                                        reverseEpisode = !reverseEpisode
                                    },
                                    scale = ClickableSurfaceScale.None,
                                    colors = ClickableSurfaceDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(
                                            BorderStroke(
                                                2.dp, MaterialTheme.colorScheme.border
                                            )
                                        )
                                    ),
                                ) {
                                    Text(
                                        text = if (reverseEpisode) "倒序" else "正序",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(8.dp, 4.dp)
                                    )
                                }
                            }
                        }
                    }) { epIndex, _ ->

                    PlaybackActivity.startActivity(
                        context, NavigateToPlayerArg(
                            animeName = videoDetail.animeName,
                            animeId = videoDetail.animeId,
                            coverUrl = videoDetail.imageUrl,
                            playIndex = epIndex,
                            playlist = playlist.episodeList,
                            sourceId = viewModel.sourceId
                        )
                    )
                }
                LaunchedEffect(reverseEpisode) {
                    listState.scrollToItem(0)
                }
            }
            item {
                RelativeVideoRow(videoDetail.otherAnimeList, viewModel.sourceId)
            }
        }, verticalArrangement = Arrangement.spacedBy(10.dp)
    )
}

@Composable
fun RelativeVideoRow(videos: List<AnimeData>, sourceId: String) {
    if (videos.isEmpty()) {
        return
    }
    val context = LocalContext.current
    Column {
        Text(text = stringResource(id = R.string.related_videos))
        Spacer(modifier = Modifier.height(5.dp))
        TvLazyRow(
            content = {
                items(items = videos, key = { it.url }) { video ->
                    VideoCard(
                        modifier = Modifier.size(
                            dimensionResource(id = R.dimen.poster_width),
                            dimensionResource(id = R.dimen.poster_height)
                        ), imageUrl = video.imageUrl, title = video.title
                    ) {
                        DetailActivity.startActivity(context, video.id, sourceId)
                    }
                }
            }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 15.dp)
        )
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun PlayListRow(
    episodes: List<AnimePlayListEpisode>,
    title: @Composable () -> Unit,
    lastPlayEpisodeId: String = "",
    listState: TvLazyListState = rememberTvLazyListState(),
    onEpisodeClick: (Int, AnimePlayListEpisode) -> Unit,
) {
    val lastEpisodeIndex = remember(episodes, lastPlayEpisodeId) {
        if (lastPlayEpisodeId.isEmpty()) {
            -1
        } else {
            episodes.indexOfFirst { it.episodeId == lastPlayEpisodeId }
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        title()
        Spacer(modifier = Modifier.height(5.dp))
        FocusGroup {
            TvLazyRow(
                state = listState, content = {
                    items(count = episodes.size, key = { episodes[it].episodeId }) { epIndex ->
                        val ep = episodes[epIndex]
                        VideoTag(
                            modifier = if (epIndex == lastEpisodeIndex || (epIndex == 0 && lastEpisodeIndex == -1)) Modifier.initiallyFocused(
                                true
                            ) else Modifier.restorableFocus(), tagName = ep.episode
                        ) {
                            onEpisodeClick(epIndex, ep)
                        }
                    }
                }, horizontalArrangement = Arrangement.spacedBy(5.dp)
            )
        }
    }
    LaunchedEffect(lastPlayEpisodeId) {
        if (lastEpisodeIndex != -1) {
            listState.scrollToItem(lastEpisodeIndex)
        }
    }
}


@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoInfoRow(videoDetail: AnimeDetailPageData, viewModel: DetailPageViewModel) {
    val focusRequester = remember {
        FocusRequester()
    }
    var showDescDialog by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.poster_height) * 1.3f + 10.dp)
    ) {
        CompactCard(
            onClick = {},
            image = {
                AsyncImage(
                    model = videoDetail.imageUrl,
                    contentDescription = videoDetail.animeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            },
            title = {},
            scale = CardDefaults.scale(focusedScale = 1f),
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(
                    dimensionResource(id = R.dimen.poster_width) * 1.3f,
                    dimensionResource(id = R.dimen.poster_height) * 1.3f
                )
        )
        Spacer(modifier = Modifier.width(15.dp))
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = videoDetail.animeName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            val playHistory = viewModel.latestProgress.collectAsState().value
            if (playHistory is Resource.Success) {
                val his = playHistory.data
                Text(
                    text = "上次播放到${his.lastEpisodeName} ${(his.lastPlayTime / 1000).secondsToMinuteAndSecondText()}/${(his.videoDuration / 1000).secondsToMinuteAndSecondText()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                TvLazyVerticalGrid(columns = TvGridCells.Fixed(2),
                    verticalArrangement = spacedBy(6.dp),
                    horizontalArrangement = spacedBy(6.dp),
                    content = {
                        items(items = videoDetail.infoList) { info ->
                            Text(text = info)
                        }
                        item(span = { TvGridItemSpan(maxLineSpan) }) {
                            Surface(
                                onClick = { showDescDialog = true },
                                scale = ClickableSurfaceScale.None,
                                colors = ClickableSurfaceDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        BorderStroke(
                                            2.dp, MaterialTheme.colorScheme.border
                                        )
                                    )
                                ),
                                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.extraSmall)
                            ) {
                                Text(
                                    text = videoDetail.description,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 3.dp
                                    )
                                )
                            }
                        }
                    })
            }

        }
    }

    // 在Dialog中显示视频简介
    AnimatedVisibility(visible = showDescDialog) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val longDescFocusRequester = remember {
            FocusRequester()
        }
        AlertDialog(onDismissRequest = { showDescDialog = false },
            confirmButton = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.6f),
            title = {
                Text(
                    text = stringResource(R.string.video_description),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            },
            text = {
                Text(text = videoDetail.description,
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .focusRequester(longDescFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent {
                            val step = 70f
                            when (it.key) {
                                Key.DirectionUp -> {
                                    if (it.type == KeyEventType.KeyDown) {
                                        coroutineScope.launch {
                                            scrollState.animateScrollBy(-step)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.DirectionDown -> {
                                    if (it.type == KeyEventType.KeyDown) {
                                        coroutineScope.launch {
                                            scrollState.animateScrollBy(step)
                                        }
                                        true
                                    } else {
                                        false
                                    }

                                }

                                else -> false
                            }

                        })
                LaunchedEffect(Unit) {
                    longDescFocusRequester.requestFocus()
                }
            }

        )

    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoTag(tagName: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(
        modifier = modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.2f),
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
            pressedContainerColor = Color.White.copy(alpha = 0.2f)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    width = 2.dp, color = MaterialTheme.colorScheme.border
                ), shape = MaterialTheme.shapes.small
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        onClick = onClick
    ) {
        Text(
            modifier = Modifier.padding(6.dp, 3.dp), text = tagName, color = Color.White
        )
    }

}