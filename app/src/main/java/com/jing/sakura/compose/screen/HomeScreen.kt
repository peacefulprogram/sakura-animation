package com.jing.sakura.compose.screen

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListScope
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.category.AnimeCategoryActivity
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.UpAndDownFocusProperties
import com.jing.sakura.compose.common.Value
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.applyUpAndDown
import com.jing.sakura.compose.common.getValue
import com.jing.sakura.compose.common.setValue
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.history.HistoryActivity
import com.jing.sakura.home.HomeViewModel
import com.jing.sakura.search.SearchActivity
import com.jing.sakura.timeline.UpdateTimelineActivity
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val context = LocalContext.current
    var showChangeSourceDialog by remember {
        mutableStateOf(false)
    }
    val currentSource = viewModel.currentSource.collectAsState().value
    val buttons = remember(currentSource) {
        listOf(

            HomeScreenButton(
                icon = Icons.Default.Search,
                backgroundColor = R.color.green400,
                display = currentSource.supportSearch(),
                label = R.string.button_search
            ) {
                SearchActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(
                icon = Icons.Default.History,
                backgroundColor = R.color.yellow500,
                label = R.string.playback_history
            ) {
                HistoryActivity.startActivity(context)
            },
            HomeScreenButton(
                icon = Icons.Default.CalendarMonth,
                backgroundColor = R.color.cyan500,
                display = currentSource.supportTimeline(),
                label = R.string.anime_update_timeline
            ) {
                UpdateTimelineActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(
                icon = Icons.Default.Category,
                backgroundColor = R.color.lime600,
                display = currentSource.supportSearchByCategory(),
                label = R.string.button_anime_category
            ) {
                AnimeCategoryActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(
                icon = Icons.Default.ChangeCircle,
                backgroundColor = R.color.blue400,
                label = R.string.button_change_anime_source
            ) {
                showChangeSourceDialog = true
            },
            HomeScreenButton(
                icon = Icons.Default.Refresh,
                backgroundColor = R.color.sky400,
                label = R.string.button_refresh
            ) {
                viewModel.loadData(false)
            },
        )
    }
    val screenTitle = viewModel.currentSource.collectAsState().value.name
    val homePageDataResource = viewModel.homePageData.collectAsState().value

    val displayData: HomePageData? = when (homePageDataResource) {
        is Resource.Success -> homePageDataResource.data
        is Resource.Loading -> viewModel.lastHomePageData
        is Resource.Error -> null
    }


    val seriesCount = displayData?.seriesList?.size ?: 0
    val focusRequesterRows = remember(seriesCount) {
        HomeScreenFocusRows(
            topButtonRow = FocusRequester(),
            seriesRows = List(seriesCount) { FocusRequester() }
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var haveSetDefaultFocus by remember(displayData) {
        Value(false)
    }
    // 上次获取焦点行索引
    var lastFocusedRowPosition by remember(currentSource.sourceId) {
        Value(FocusedVideoPosition.DEFAULT)
    }

    val videoWidth = dimensionResource(id = R.dimen.poster_width)
    val videoHeight = dimensionResource(id = R.dimen.poster_height)
    val focusRestoreStateMap = remember(currentSource.sourceId) {
        mutableMapOf<String, FocusPositionRestoreState<String>>()
    }
    LaunchedEffect(displayData) {
        val list = displayData?.seriesList ?: emptyList()
        val newMap = list.associate {
            it.name to (focusRestoreStateMap[it.name] ?: FocusPositionRestoreState())
        }
        focusRestoreStateMap.clear()
        focusRestoreStateMap.putAll(newMap)
    }
    val rowStateMap = remember(currentSource.sourceId) {
        mutableMapOf<String, TvLazyListState>()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val columnState = rememberTvLazyListState()
        TvLazyColumn(
            state = columnState,
            modifier = Modifier
                .fillMaxWidth(),
            content = {
                item {
                    HomeTitleRow(
                        buttonList = buttons,
                        title = screenTitle,
                        focusRequester = focusRequesterRows.topButtonRow,
                        upAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT,
                        iconSize = 50.dp,
                        iconFocusedScale = 1.25f
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LaunchedEffect(seriesCount) {
                        if (seriesCount == 0) {
                            focusRequesterRows.topButtonRow.requestFocus()
                        }
                    }
                }
                if (displayData != null && seriesCount > 0) {
                    val seriesList = displayData.seriesList
                    videoRows(
                        videoSeries = seriesList,
                        sourceId = currentSource.sourceId,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        focusRequesterList = focusRequesterRows.seriesRows,
                        focusRestoreStateProvider = {
                            val state = focusRestoreStateMap[it]
                            if (state == null) {
                                val newState = FocusPositionRestoreState<String>()
                                focusRestoreStateMap[it] = newState
                                newState
                            } else {
                                state
                            }
                        },
                        onBackPressed = {
                            if (columnState.firstVisibleItemIndex == 0) {
                                false
                            } else {
                                coroutineScope.launch {
                                    columnState.scrollToItem(0)
                                    focusRequesterRows.seriesRows.firstOrNull()?.requestFocus()
                                }
                                true
                            }
                        },
                        onVideoFocused = { groupName, video ->
                            lastFocusedRowPosition =
                                FocusedVideoPosition(groupName = groupName, videoId = video.id)
                        },
                        listState = { groupName ->
                            val state = rowStateMap[groupName]
                            if (state == null) {
                                val newState = TvLazyListState()
                                rowStateMap[groupName] = newState
                                newState
                            } else {
                                state
                            }
                        },
                        onRequestRefresh = { viewModel.loadData(false) }
                    ) {
                        DetailActivity.startActivity(
                            context,
                            it.id,
                            currentSource.sourceId
                        )
                    }
                }
            }
        )


        val seriesList = displayData?.seriesList ?: emptyList()
        LaunchedEffect(displayData) {
            if (!haveSetDefaultFocus && seriesList.isNotEmpty()) {
                haveSetDefaultFocus = true
                var groupIndex = 0
                var videoIndex = 0
                if (lastFocusedRowPosition.groupName.isNotEmpty() && lastFocusedRowPosition.videoId.isNotEmpty()) {
                    val gIndex =
                        seriesList.indexOfFirst { it.name == lastFocusedRowPosition.groupName }
                    if (gIndex >= 0) {
                        val vIndex =
                            seriesList.getOrNull(gIndex)?.value?.indexOfFirst { it.id == lastFocusedRowPosition.videoId }
                                ?: -1
                        if (vIndex >= 0) {
                            groupIndex = gIndex
                            videoIndex = vIndex
                        }
                    }
                }
                val rowIndex = groupIndex * 2 + 1
                if (rowIndex !in visibleItemIndex(columnState)) {
                    columnState.scrollToItem(rowIndex)
                }
                val groupName = seriesList[groupIndex].name
                focusRestoreStateMap[groupName]?.rowListState?.let { state ->
                    if (videoIndex !in visibleItemIndex(state)) {
                        state.scrollToItem(
                            videoIndex
                        )
                    }
                }
                runCatching {
                    focusRestoreStateMap[groupName]?.focusRequesterMap?.get(seriesList[groupIndex].value[videoIndex].id)
                        ?.requestFocus()
                }.onFailure {
                    focusRequesterRows.seriesRows[groupIndex].requestFocus()
                }
            }
        }

        if (homePageDataResource is Resource.Loading && !homePageDataResource.silent) {
            Loading(text = "")
        }
        if (homePageDataResource is Resource.Error) {
            ErrorTip(message = homePageDataResource.message) {
                viewModel.loadData(false)
            }
        }
    }

    if (showChangeSourceDialog) {
        ChangeSourceDialog(
            viewModel.getAllSources(),
            viewModel.currentSourceId,
            onDismissRequest = { showChangeSourceDialog = false }) {
            viewModel.changeSource(it)
            showChangeSourceDialog = false
        }
    }
}

private fun visibleItemIndex(listState: TvLazyListState): List<Int> {
    val layoutInfo = listState.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) {
        return emptyList()
    }
    val fullyVisibleItemsInfo = visibleItemsInfo.toMutableList()

    val lastItem = fullyVisibleItemsInfo.last()

    val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset

    if (lastItem.offset + lastItem.size > viewportHeight) {
        fullyVisibleItemsInfo.removeLast()
    }

    val firstItemIfLeft = fullyVisibleItemsInfo.firstOrNull()
    if (firstItemIfLeft != null && firstItemIfLeft.offset < layoutInfo.viewportStartOffset) {
        fullyVisibleItemsInfo.removeFirst()
    }

    return fullyVisibleItemsInfo.map { it.index }
}

@OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class
)
fun TvLazyListScope.videoRows(
    videoSeries: List<NamedValue<List<AnimeData>>>,
    sourceId: String,
    videoWidth: Dp,
    videoHeight: Dp,
    focusRestoreStateProvider: (groupName: String) -> FocusPositionRestoreState<String>,
    listState: (groupName: String) -> TvLazyListState,
    focusRequesterList: List<FocusRequester> = emptyList(),
    onVideoFocused: (groupName: String, video: AnimeData) -> Unit,
    onBackPressed: () -> Boolean,
    onRequestRefresh: () -> Unit,
    onClick: (video: AnimeData) -> Unit
) {
    val focusScale = 1.1f
    val paddingPercent = (focusScale - 1) / 2 + 0.01f
    val horizontalPadding = videoWidth * paddingPercent
    val verticalPadding = videoWidth * paddingPercent
    for ((groupIndex, videoGroup) in videoSeries.withIndex()) {
        val (groupName, videos) = videoGroup
        item(key = sourceId to "t$groupName") {
            Text(
                text = groupName,
                modifier = Modifier.padding(start = horizontalPadding),
                style = MaterialTheme.typography.titleLarge
            )
        }
        item(key = sourceId to groupName) {

            val focusRestoreState = focusRestoreStateProvider(groupName)
            val rowListState = listState(groupName)
            focusRestoreState.rowListState = rowListState
            val pivotOffsets = PivotOffsets()
            LaunchedEffect(videos) {
                val ids = videos.asSequence().map { it.id }.toSet()
                focusRestoreState.focusRequesterMap.keys.forEach {
                    if (!ids.contains(it)) {
                        focusRestoreState.focusRequesterMap.remove(it)
                    }
                }
                val lastFocusItem = focusRestoreState.lastFocusItem
                var scrollIndex = 0
                if (lastFocusItem != null) {
                    scrollIndex =
                        videos.indexOfFirst { it.id == lastFocusItem }.takeIf { it > -1 } ?: 0
                }
                rowListState.scrollToItem(
                    scrollIndex,
                    -(pivotOffsets.parentFraction * rowListState.layoutInfo.viewportSize.width).toInt()
                )
            }
            TvLazyRow(
                state = rowListState,
                pivotOffsets = pivotOffsets,
                modifier = Modifier
                    .focusProperties {
                        enter = {
                            focusRestoreState.lastFocusItem?.let { focusRestoreState.focusRequesterMap[it] }
                                ?: focusRestoreState.initiallyFocusItem?.let { focusRestoreState.focusRequesterMap[it] }
                                ?: FocusRequester.Default
                        }
                    }
                    .focusRequester(focusRequesterList[groupIndex]),
                contentPadding = PaddingValues(
                    vertical = verticalPadding,
                    horizontal = horizontalPadding
                ),
                content = {
                    items(count = videos.size, key = { videos[it].id }) { videoIndex ->
                        val video = videos[videoIndex]
                        val focusRequester = remember {
                            FocusRequester()
                        }
                        if (videoIndex == 0) {
                            focusRestoreState.initiallyFocusItem = video.id
                        }
                        focusRestoreState.focusRequesterMap[video.id] = focusRequester
                        VideoCard(
                            modifier = Modifier
                                .size(width = videoWidth, height = videoHeight)
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    if (it.hasFocus || it.isFocused) {
                                        focusRestoreState.lastFocusItem = video.id
                                        onVideoFocused(groupName, video)
                                    }
                                },
                            focusScale = focusScale,
                            imageUrl = video.imageUrl,
                            title = video.title,
                            subTitle = video.currentEpisode,
                            onKeyEvent = { keyEvent ->
                                if (keyEvent.key == Key.Menu) {
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        onRequestRefresh()
                                    }
                                    true
                                } else if (keyEvent.key == Key.DirectionLeft && videoIndex == 0) {
                                    true
                                } else if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                    onBackPressed()
                                } else {
                                    false
                                }
                            },
                            onClick = {
                                onClick(video)
                            }
                        )
                    }
                })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChangeSourceDialog(
    allSources: List<Pair<String, String>>,
    currentSourceId: String,
    onDismissRequest: () -> Unit,
    onChangeSource: (sourceId: String) -> Unit,
) {
    val defaultIndex = remember(allSources, currentSourceId) {
        allSources.indexOfFirst { it.first == currentSourceId }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val listState =
            rememberTvLazyListState(initialFirstVisibleItemIndex = defaultIndex)
        val focusRequester = remember {
            FocusRequester()
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .width(400.dp)

        ) {
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
                    SourceItem(
                        source = source,
                        modifier = modifier,
                        textColor = if (currentSourceId == source.first) colorResource(id = R.color.cyan300) else MaterialTheme.colorScheme.onSurface
                    ) {
                        onChangeSource(source.first)
                    }
                }
            })
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun SourceItem(
    modifier: Modifier = Modifier,
    textColor: Color,
    source: Pair<String, String>,
    onClick: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }
    Text(text = source.second,
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) colorResource(id = R.color.gray800) else Color.Transparent)
            .padding(10.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .focusable()
            .clickable(onClick = onClick)
    )

}

@OptIn(
    ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class
)
@Composable
fun HomeTitleRow(
    modifier: Modifier = Modifier,
    buttonList: List<HomeScreenButton>,
    title: String,
    focusRequester: FocusRequester,
    upAndDownFocusProperties: UpAndDownFocusProperties,
    iconSize: Dp = 40.dp,
    iconFocusedScale: Float = 1.1f
) {
    val iconPadding = iconSize / 4
    val displayButtons = remember(buttonList) {
        buttonList.filter { it.display }
    }
    FocusGroup(Modifier.focusRequester(focusRequester)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {

            TvLazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = iconSize / 2 * (iconFocusedScale - 1f)),
                content = {
                    items(
                        count = displayButtons.size,
                        key = { displayButtons[it].label }
                    ) { btnIndex ->
                        val btn = displayButtons[btnIndex]
                        val bgColor = colorResource(id = btn.backgroundColor)
                        val label = stringResource(id = btn.label)
                        Button(
                            onClick = btn.onClick,
                            shape = ButtonDefaults.shape(shape = CircleShape),
                            scale = ButtonDefaults.scale(focusedScale = iconFocusedScale),
                            colors = ButtonDefaults.colors(
                                containerColor = bgColor,
                                focusedContainerColor = bgColor
                            ),
                            modifier = Modifier
                                .size(iconSize)
                                .focusProperties { applyUpAndDown(upAndDownFocusProperties) }
                                .run {
                                    if (btnIndex == 0) initiallyFocused() else restorableFocus()
                                },
                            contentPadding = PaddingValues(iconPadding)
                        ) {
                            Icon(
                                imageVector = btn.icon,
                                contentDescription = label,
                                tint = colorResource(id = btn.tint)
                            )
                        }
                    }
                }
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.3),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

}

data class HomeScreenButton(
    val icon: ImageVector,
    @ColorRes
    val backgroundColor: Int,
    @ColorRes
    val tint: Int = R.color.gray100,
    @StringRes
    val label: Int,
    val display: Boolean = true,
    val onClick: () -> Unit = {},
)

private data class HomeScreenFocusRows(
    val topButtonRow: FocusRequester,
    val seriesRows: List<FocusRequester>
)


private data class FocusedVideoPosition(
    val groupName: String,
    val videoId: String
) {
    companion object {
        val DEFAULT = FocusedVideoPosition(
            groupName = "",
            videoId = ""
        )
    }
}

data class FocusPositionRestoreState<T>(
    var focusRequesterMap: MutableMap<T, FocusRequester> = mutableMapOf(),
    var initiallyFocusItem: T? = null,
    var lastFocusItem: T? = null,
    var rowListState: TvLazyListState? = null
)

