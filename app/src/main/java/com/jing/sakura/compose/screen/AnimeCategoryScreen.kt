package com.jing.sakura.compose.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.home.CategoryViewModel
import com.jing.sakura.repo.VideoCategoryGroup
import kotlinx.coroutines.launch

@Composable
fun AnimeCategoryScreen(viewModel: CategoryViewModel) {
    val categoriesResource = viewModel.categories.collectAsState().value
    if (categoriesResource is Resource.Loading) {
        Loading()
        return
    } else if (categoriesResource is Resource.Error) {
        ErrorTip(message = categoriesResource.message) {
            viewModel.loadCategories()
        }
        return
    }

    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val selectedValue = viewModel.userSelectedCategories.collectAsState().value
    var showCategoryDialog by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val refreshState = pagingItems.loadState.refresh
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    LaunchedEffect(selectedCategories) {
        if (selectedCategories.isNotEmpty()) {
            pagingItems.refresh()
        }
    }
    when (refreshState) {
        is LoadState.Loading -> {
            Loading()
            return
        }

        is LoadState.Error -> {
            ErrorTip(message = "加载失败:${refreshState.error.message}") {
                pagingItems.refresh()
            }
            return
        }

        is LoadState.NotLoading -> {
            VideoGrid(
                pagingItems = pagingItems,
                onClick = {
                    DetailActivity.startActivity(
                        context = context,
                        it.id,
                        sourceId = viewModel.sourceId
                    )
                }) {
                showCategoryDialog = true
            }
        }
    }
    if (showCategoryDialog) {
        VideoCategoryDialog(
            categoryGroups = (categoriesResource as Resource.Success<List<VideoCategoryGroup.NormalCategoryGroup>>).data,
            selectedValue = selectedValue,
            onSelect = { key, value ->
                viewModel.onUserSelect(key, value)
            },
            onApply = {
                showCategoryDialog = false
                viewModel.applyUserSelectedCategories()
            }
        )

    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoGrid(
    pagingItems: LazyPagingItems<AnimeData>,
    onClick: (video: AnimeData) -> Unit,
    onLongClick: (video: AnimeData?) -> Unit
) {
    val gridState = rememberTvLazyGridState()
    val cardWidth = dimensionResource(id = R.dimen.poster_width)
    val cardHeight = dimensionResource(id = R.dimen.poster_height)
    val scale = 1.1f
    val containerWidth = cardWidth * scale
    val containerHeight = cardHeight * scale
    val firstItemFocusRequester = remember {
        FocusRequester()
    }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(containerWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            content = {
                item(span = { TvGridItemSpan(maxLineSpan) }) {
                    Text(text = "搜索结果", style = MaterialTheme.typography.headlineMedium)
                }
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.id }
                ) { index ->
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
                            onClick = { onClick(video) },
                            onLongClick = { onLongClick(video) },
                            onKeyEvent = {
                                if (it.key == Key.Back && gridState.firstVisibleItemIndex > 0) {
                                    if (it.type == KeyEventType.KeyUp) {
                                        coroutineScope.launch {
                                            gridState.scrollToItem(0)
                                            kotlin.runCatching { firstItemFocusRequester.requestFocus() }
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                    }
                }
            })


        if (pagingItems.itemCount == 0) {
            Text(
                text = "什么都没有哦",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .focusRequester(firstItemFocusRequester)
                    .focusable()
                    .clickable { onLongClick(null) }
            )
            LaunchedEffect(Unit) {
                kotlin.runCatching { firstItemFocusRequester.requestFocus() }
            }
        }
    }


    val refreshState = pagingItems.loadState.refresh
    LaunchedEffect(refreshState) {
        if (refreshState is LoadState.NotLoading && pagingItems.itemCount > 0) {
            kotlin.runCatching { firstItemFocusRequester.requestFocus() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun VideoCategoryDialog(
    categoryGroups: List<VideoCategoryGroup.NormalCategoryGroup>,
    selectedValue: Map<String, String>,
    onSelect: (key: String, value: String) -> Unit,
    onApply: () -> Unit
) {
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize
    val fontSizeDpValue = with(LocalDensity.current) {
        fontSize.toDp()
    }
    var haveSetDefaultFocus = remember {
        false
    }
    val groupTitleWidth = remember(categoryGroups) {
        val maxTitleLength = categoryGroups.maxOfOrNull { it.name.length } ?: 4
        fontSizeDpValue * (maxTitleLength + 1)
    }
    AlertDialog(
        onDismissRequest = onApply,
        confirmButton = {},
        title = { Text(text = "选择分类") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        text = {
            val defaultFocusRequester = remember {
                FocusRequester()
            }
            ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                TvLazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    content = {
                        items(
                            count = categoryGroups.size,
                            key = { categoryGroups[it].key }) { groupIndex ->
                            val categoryGroup = categoryGroups[groupIndex]
                            val selectedIndex = remember {
                                categoryGroup.categories.indexOfFirst { it.value == selectedValue[categoryGroup.key] }
                            }
                            val rowState = rememberTvLazyListState(selectedIndex)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "${categoryGroup.name}:",
                                    Modifier.width(groupTitleWidth),
                                    textAlign = TextAlign.Right,
                                    fontSize = fontSize
                                )
                                FocusGroup(
                                    modifier = if (groupIndex == 0)
                                        Modifier.focusRequester(defaultFocusRequester)
                                    else Modifier
                                ) {
                                    TvLazyRow(
                                        state = rowState,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        contentPadding = PaddingValues(3.dp),
                                        pivotOffsets = PivotOffsets(0f),
                                        content = {
                                            items(
                                                count = categoryGroup.categories.size,
                                                key = { categoryGroup.categories[it].label }) { categoryIndex ->
                                                val category =
                                                    categoryGroup.categories[categoryIndex]
                                                FilterChip(
                                                    selected = selectedValue[categoryGroup.key] == category.value,
                                                    onClick = {
                                                        onSelect(
                                                            categoryGroup.key,
                                                            category.value
                                                        )
                                                    },
                                                    modifier = if (categoryIndex == selectedIndex) Modifier.initiallyFocused() else Modifier.restorableFocus(),
                                                    scale = FilterChipDefaults.scale(focusedScale = 1f)
                                                ) {
                                                    Text(text = category.label)
                                                }
                                            }
                                        }
                                    )
                                }
                                LaunchedEffect(Unit) {
                                    if (groupIndex == 0 && !haveSetDefaultFocus) {
                                        haveSetDefaultFocus = true
                                        defaultFocusRequester.requestFocus()
                                    }
                                }

                            }

                        }

                    }
                )
            }
        })
}