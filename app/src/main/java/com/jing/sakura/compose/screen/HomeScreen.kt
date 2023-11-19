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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
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
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
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
    val coroutineScope = rememberCoroutineScope()
    val defaultFocusRequester = remember {
        FocusRequester()
    }
    var haveSetDefaultFocus = remember {
        false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val columnState = rememberTvLazyListState()

        TvLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxWidth(),
            content = {
                item {
                    HomeTitleRow(
                        buttonList = buttons,
                        title = screenTitle,
                        iconSize = 50.dp,
                        iconFocusedScale = 1.25f
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                val displayData: HomePageData? =
                    if (homePageDataResource is Resource.Error) null else if (homePageDataResource is Resource.Success) homePageDataResource.data else viewModel.lastHomePageData
                if (displayData != null) {
                    val seriesList = displayData.seriesList
                    items(
                        count = seriesList.size,
                        key = { seriesList[it].name }
                    ) { videoCategoryIndex ->
                        val videoCategory = seriesList[videoCategoryIndex]
                        AnimationRow(
                            if (videoCategoryIndex == 0) Modifier.focusRequester(
                                defaultFocusRequester
                            ) else Modifier,
                            title = videoCategory.name,
                            videos = videoCategory.value,
                            onRequestRefresh = { viewModel.loadData(false) },
                            onVideoClick = { video ->
                                DetailActivity.startActivity(
                                    context,
                                    video.id,
                                    viewModel.currentSourceId
                                )
                            }
                        ) {
                            if (columnState.firstVisibleItemIndex == 0) {
                                false
                            } else {
                                coroutineScope.launch {
                                    columnState.scrollToItem(0)
                                    defaultFocusRequester.requestFocus()
                                }
                                true
                            }
                        }
                        LaunchedEffect(Unit) {
                            if (!haveSetDefaultFocus) {
                                haveSetDefaultFocus = true
                                defaultFocusRequester.requestFocus()
                            }
                        }
                    }
                }
            }
        )

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
    ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun HomeTitleRow(
    modifier: Modifier = Modifier,
    buttonList: List<HomeScreenButton>,
    title: String,
    iconSize: Dp = 40.dp,
    iconFocusedScale: Float = 1.1f
) {
    val iconPadding = iconSize / 4
    val displayButtons = remember(buttonList) {
        buttonList.filter { it.display }
    }
    val coroutineScope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        FocusGroup {
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

        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.3),
            color = MaterialTheme.colorScheme.onSurface
        )
    }

}


@OptIn(ExperimentalTvFoundationApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun AnimationRow(
    modifier: Modifier = Modifier,
    title: String,
    videos: List<AnimeData>,
    onRequestRefresh: () -> Unit,
    onVideoClick: (video: AnimeData) -> Unit,
    onBackPressed: () -> Boolean
) {
    val focusScale = 1.1f
    val width = dimensionResource(id = R.dimen.poster_width)
    val height = dimensionResource(id = R.dimen.poster_height)
    val edgeBlankWidth = width * (focusScale - 1f)
    FocusGroup(modifier) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(height * 0.1f))
            Row {
                Spacer(modifier = Modifier.width(edgeBlankWidth))
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TvLazyRow(
                contentPadding = PaddingValues(horizontal = edgeBlankWidth),
                content = {
                    items(count = videos.size, key = { videos[it].id }) { videoIndex ->
                        val video = videos[videoIndex]
                        VideoCard(
                            modifier = Modifier.size(width = width, height = height)
                                .run {
                                    if (videoIndex == 0) {
                                        initiallyFocused()
                                    } else {
                                        restorableFocus()
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
                                onVideoClick(video)
                            }
                        )
                    }
                })
            Spacer(modifier = Modifier.height(height * 0.05f))
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



