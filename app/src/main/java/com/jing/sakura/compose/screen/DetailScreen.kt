@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
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
import com.jing.sakura.compose.common.UpAndDownFocusProperties
import com.jing.sakura.compose.common.Value
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.applyUpAndDown
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.PlaybackActivity
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
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

    val currentFocusedEpisodeId = remember {
        Value("")
    }

    val videoHistory = viewModel.latestProgress.collectAsState().value.getOrNull()

    val nextPlayListId = remember {
        Value(0)
    }

    val playlists =
        remember(reverseEpisode, videoHistory?.episodeId) {
            if (reverseEpisode) {
                videoDetail.playLists.map {
                    PlayListWrapper(
                        id = nextPlayListId.value++,
                        playlist = it.copy(episodeList = it.episodeList.reversed())
                    )
                }
            } else {
                videoDetail.playLists.map {
                    PlayListWrapper(
                        id = nextPlayListId.value++,
                        playlist = it
                    )
                }
            }
        }

    val focusedEpisodeIndex = remember(videoHistory?.episodeId) {
        val episodeId = videoHistory?.episodeId
        var lastPlayPosition = 0 to 0
        if (episodeId != null) {
            out@ for ((playListIndex, playList) in playlists.withIndex()) {
                for ((episodeIndex, episode) in playList.playlist.episodeList.withIndex()) {
                    if (episodeId == episode.episodeId) {
                        lastPlayPosition = playListIndex to episodeIndex
                        break@out
                    }
                }
            }
        }
        Value(MutableList(videoDetail.playLists.size) { if (it == lastPlayPosition.first) lastPlayPosition.second else 0 })
    }

    // 切换正序/倒序排序时, 更新索引
    val changeFocusedEpisodeIndexForReversed = {
        videoDetail.playLists.forEachIndexed { index, _ ->
            focusedEpisodeIndex.value[index] = 0
        }
    }

    val focusRequesters = remember(playlists.size, videoDetail.otherAnimeList.isEmpty()) {
        DetailPageRowFocusRequesters(
            infoRow = FocusRequester(),
            playListReverseButton = if (playlists.isNotEmpty()) FocusRequester() else null,
            playListRows = List(playlists.size) { FocusRequester() },
            otherAnimeRow = if (videoDetail.otherAnimeList.isNotEmpty()) FocusRequester() else null
        )
    }

    val shouldFocusReverseButton = remember {
        Value(false)
    }

    // 进入播放页,返回后恢复焦点
    val restoreEpisodeFocusRequester = remember {
        FocusRequester()
    }
    val restoreEpisodePosition = remember {
        Value(-1 to -1)
    }

    val initialFocusSet = remember {
        Value(false)
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(), content = {
            item {
                VideoInfoRow(
                    videoDetail = videoDetail,
                    modifier = Modifier
                        .focusRequester(focusRequesters.infoRow),
                    upAndDownFocusProperties = UpAndDownFocusProperties(
                        down = focusRequesters.playListReverseButton
                            ?: focusRequesters.otherAnimeRow
                    ),
                    playHistory = videoHistory
                ) {
                    viewModel.loadData()
                }
                LaunchedEffect(Unit) {
                    if (!initialFocusSet.value) {
                        initialFocusSet.value = true
                        runCatching { focusRequesters.infoRow.requestFocus() }.onFailure { it.printStackTrace() }
                    }
                }
            }
            items(
                count = playlists.size,
                key = { playlists[it].id }
            ) { playlistIndex ->
                val playlist = playlists[playlistIndex].playlist
                val initiallyFocusedIndex = focusedEpisodeIndex.value[playlistIndex]
                val listState =
                    rememberTvLazyListState(initialFirstVisibleItemIndex = initiallyFocusedIndex)
                PlayListRow(
                    episodes = playlist.episodeList,
                    modifier = Modifier
                        .focusRequester(focusRequesters.playListRows[playlistIndex]),
                    initiallyFocusedIndex = initiallyFocusedIndex,
                    listState = listState,
                    onEpisodeFocused = { epIndex, ep ->
                        focusedEpisodeIndex.value[playlistIndex] = epIndex
                        currentFocusedEpisodeId.value = ep.episodeId
                    },
                    restoreFocusEpIndex = if (restoreEpisodePosition.value.first == playlistIndex) restoreEpisodePosition.value.second else -1,
                    restoreFocusRequester = restoreEpisodeFocusRequester,
                    upAndDownFocusProperties = UpAndDownFocusProperties(
                        up = focusRequesters.playListRows.getOrNull(playlistIndex - 1)
                            ?: focusRequesters.playListReverseButton, // 上一个播放列表 或者正序倒序按钮
                        down = focusRequesters.playListRows.getOrNull(playlistIndex + 1)
                            ?: focusRequesters.otherAnimeRow // 下一个播放列表或者推荐视频
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (playlistIndex == 0) {
                                Text(text = " | ")
                                Surface(
                                    onClick = {
                                        changeFocusedEpisodeIndexForReversed()
                                        shouldFocusReverseButton.value = true
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
                                    modifier = Modifier
                                        .focusRequester(focusRequesters.playListReverseButton!!)
                                        .focusProperties {
                                            up = focusRequesters.infoRow
                                            down = focusRequesters.playListRows[0]
                                        }
                                ) {
                                    Text(
                                        text = if (reverseEpisode) "倒序" else "正序",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(8.dp, 4.dp)
                                    )
                                }
                            }
                            LaunchedEffect(shouldFocusReverseButton) {
                                if (shouldFocusReverseButton.value) {
                                    shouldFocusReverseButton.value = false
                                    runCatching { focusRequesters.playListReverseButton?.requestFocus() }
                                }
                            }
                        }
                    }) { epIndex, _ ->
                    restoreEpisodePosition.value = playlistIndex to epIndex
                    PlaybackActivity.startActivity(
                        context,
                        NavigateToPlayerArg(
                            animeName = videoDetail.animeName,
                            animeId = videoDetail.animeId,
                            coverUrl = videoDetail.imageUrl,
                            playIndex = epIndex,
                            playlist = playlist.episodeList,
                            sourceId = viewModel.sourceId
                        )
                    )
                }
            }
            item {
                if (videoDetail.otherAnimeList.isNotEmpty()) {
                    RelativeVideoRow(
                        videoDetail.otherAnimeList,
                        viewModel.sourceId,
                        Modifier
                            .focusRequester(focusRequesters.otherAnimeRow!!),
                        upAndDownFocusProperties = UpAndDownFocusProperties(
                            up = focusRequesters.playListRows.lastOrNull()
                                ?: focusRequesters.infoRow
                        )
                    )
                }
            }
        }, verticalArrangement = Arrangement.spacedBy(10.dp)
    )

    LaunchedEffect(restoreEpisodePosition.value) {
        if (restoreEpisodePosition.value.first >= 0 && restoreEpisodePosition.value.second >= 0) {
            restoreEpisodePosition.value = -1 to -1
            runCatching {
                restoreEpisodeFocusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun RelativeVideoRow(
    videos: List<AnimeData>,
    sourceId: String,
    modifier: Modifier,
    upAndDownFocusProperties: UpAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT
) {
    if (videos.isEmpty()) {
        return
    }
    val context = LocalContext.current
    FocusGroup(modifier) {
        Column {
            Text(text = stringResource(id = R.string.related_videos))
            Spacer(modifier = Modifier.height(5.dp))
            TvLazyRow(
                content = {
                    items(count = videos.size, key = { videos[it].id }) { videoIndex ->
                        val video = videos[videoIndex]
                        VideoCard(
                            modifier = Modifier
                                .size(
                                    dimensionResource(id = R.dimen.poster_width),
                                    dimensionResource(id = R.dimen.poster_height)
                                )
                                .focusProperties { applyUpAndDown(upAndDownFocusProperties) }
                                .run {
                                    if (videoIndex == 0) initiallyFocused() else restorableFocus()
                                },
                            imageUrl = video.imageUrl,
                            title = video.title
                        ) {
                            DetailActivity.startActivity(context, video.id, sourceId)
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 15.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun PlayListRow(
    episodes: List<AnimePlayListEpisode>,
    modifier: Modifier,
    title: @Composable () -> Unit,
    listState: TvLazyListState,
    restoreFocusRequester: FocusRequester,
    restoreFocusEpIndex: Int,
    upAndDownFocusProperties: UpAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT,
    initiallyFocusedIndex: Int = 0,
    onEpisodeFocused: (Int, AnimePlayListEpisode) -> Unit,
    onEpisodeClick: (Int, AnimePlayListEpisode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        title()
        Spacer(modifier = Modifier.height(5.dp))
        FocusGroup(modifier) {
            TvLazyRow(
                state = listState,
                pivotOffsets = PivotOffsets(0f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                content = {
                    items(count = episodes.size, key = { episodes[it].episodeId }) { epIndex ->
                        val ep = episodes[epIndex]
                        val episodeModifier =
                            if (epIndex == initiallyFocusedIndex) {
                                Modifier.initiallyFocused()
                            } else Modifier.restorableFocus()
                        VideoEpisode(
                            modifier = episodeModifier
                                .run {
                                    if (restoreFocusEpIndex == epIndex) {
                                        focusRequester(restoreFocusRequester)
                                    } else {
                                        this
                                    }
                                }
                                .focusProperties {
                                    applyUpAndDown(upAndDownFocusProperties)
                                }
                                .onFocusChanged {
                                    if (it.hasFocus || it.isFocused) {
                                        onEpisodeFocused(epIndex, ep)
                                    }
                                },
                            tagName = ep.episode
                        ) {
                            onEpisodeClick(epIndex, ep)
                        }
                    }
                }
            )
        }
    }
}


@OptIn(
    ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalTvFoundationApi::class
)
@Composable
fun VideoInfoRow(
    videoDetail: AnimeDetailPageData,
    modifier: Modifier,
    upAndDownFocusProperties: UpAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT,
    playHistory: VideoHistoryEntity? = null,
    onCoverClick: () -> Unit = {}
) {
    var showDescDialog by remember {
        mutableStateOf(false)
    }

    FocusGroup(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(dimensionResource(id = R.dimen.poster_height) * 1.3f + 10.dp)
        ) {
            CompactCard(
                onClick = {
                    onCoverClick()
                },
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
                    .initiallyFocused()
                    .size(
                        dimensionResource(id = R.dimen.poster_width) * 1.3f,
                        dimensionResource(id = R.dimen.poster_height) * 1.3f
                    )
                    .focusProperties { applyUpAndDown(upAndDownFocusProperties) }
                    .padding(2.dp)
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
                if (playHistory != null) {
                    Text(
                        text = "上次播放到${playHistory.lastEpisodeName} ${(playHistory.lastPlayTime / 1000).secondsToMinuteAndSecondText()}/${(playHistory.videoDuration / 1000).secondsToMinuteAndSecondText()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        content = {
                            items(items = videoDetail.infoList) { info ->
                                Text(text = info, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (videoDetail.description.isNotEmpty()) {
                                item(span = { TvGridItemSpan(maxLineSpan) }) {
                                    Surface(
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .focusProperties {
                                                applyUpAndDown(
                                                    upAndDownFocusProperties
                                                )
                                            }
                                            .restorableFocus(),
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

                            }
                        })
                }

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

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoEpisode(tagName: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    var focused by remember {
        mutableStateOf(false)
    }
    Surface(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .run {
                if (focused) {
                    border(
                        2.dp,
                        MaterialTheme.colorScheme.border,
                        MaterialTheme.shapes.small
                    )
                } else {
                    this
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.2f),
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
            pressedContainerColor = Color.White.copy(alpha = 0.2f)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        border = ClickableSurfaceDefaults.border(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        onClick = onClick
    ) {
        Text(
            modifier = Modifier
                .padding(6.dp, 3.dp),
            text = tagName,
            color = Color.White,
        )
    }
}

private data class PlayListWrapper(
    val id: Int,
    val playlist: AnimePlayList
)

private data class DetailPageRowFocusRequesters(
    val infoRow: FocusRequester,
    val playListReverseButton: FocusRequester?,
    val playListRows: List<FocusRequester>,
    val otherAnimeRow: FocusRequester?
)

