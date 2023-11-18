package com.jing.sakura.compose.screen

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.theme.SakuraTheme
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
    val buttons = remember {
        listOf(
            HomeScreenButton(icon = R.drawable.search_icon, backgroundColor = R.color.green400) {
                SearchActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(icon = R.drawable.history_icon, backgroundColor = R.color.yellow500) {
                HistoryActivity.startActivity(context)
            },
            HomeScreenButton(icon = R.drawable.timeline_icon, backgroundColor = R.color.cyan500) {
                UpdateTimelineActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(icon = R.drawable.switch_icon, backgroundColor = R.color.blue400) {
                showChangeSourceDialog = true
            },
            HomeScreenButton(icon = R.drawable.refresh_icon, backgroundColor = R.color.sky400) {
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
                    if (homePageDataResource is Resource.Success) homePageDataResource.data else viewModel.lastHomePageData
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
            Loading()
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun HomeTitleRow(
    modifier: Modifier = Modifier,
    buttonList: List<HomeScreenButton>,
    title: String,
    iconSize: Dp = 40.dp,
    iconFocusedScale: Float = 1.1f
) {
    val iconPadding = iconSize / 4
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        FocusGroup {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                buttonList.forEachIndexed { btnIndex, btn ->
                    val bgColor = colorResource(id = btn.backgroundColor)
                    Button(
                        onClick = btn.onClick,
                        shape = ButtonDefaults.shape(shape = CircleShape),
                        scale = ButtonDefaults.scale(focusedScale = iconFocusedScale),
                        colors = ButtonDefaults.colors(
                            containerColor = bgColor,
                            focusedContainerColor = bgColor
                        ),
                        modifier = Modifier.size(iconSize).run {
                            if (btnIndex == 0) initiallyFocused() else restorableFocus()
                        },
                        contentPadding = PaddingValues(iconPadding)
                    ) {
                        Icon(
                            painter = painterResource(id = btn.icon),
                            contentDescription = btn.label,
                            tint = colorResource(id = btn.tint)
                        )
                    }
                }
            }

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
            TvLazyRow(content = {
                item {
                    Spacer(modifier = Modifier.width(edgeBlankWidth))
                }
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
                item {
                    Spacer(modifier = Modifier.width(edgeBlankWidth))
                }
            })
            Spacer(modifier = Modifier.height(height * 0.05f))
        }
    }

}

data class HomeScreenButton(
    @DrawableRes
    val icon: Int,
    @ColorRes
    val backgroundColor: Int,
    @ColorRes
    val tint: Int = R.color.gray100,
    val label: String = "",
    val onClick: () -> Unit = {}
)

@Preview
@Composable
fun HomeTitleRowPreview() {
    SakuraTheme {
        val buttons = remember {
            listOf(
                HomeScreenButton(icon = R.drawable.search_icon, backgroundColor = R.color.green400),
                HomeScreenButton(
                    icon = R.drawable.history_icon,
                    backgroundColor = R.color.yellow500
                ),
                HomeScreenButton(
                    icon = R.drawable.timeline_icon,
                    backgroundColor = R.color.cyan500
                ),
                HomeScreenButton(icon = R.drawable.refresh_icon, backgroundColor = R.color.sky400),
            )
        }
        HomeTitleRow(buttonList = buttons, title = "MX动漫")
    }
}


