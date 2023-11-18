@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.timeline.TimelineViewModel

@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val timeline = viewModel.timelines.collectAsState().value
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize()) {
            Text(text = "更新时间表", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(15.dp))
            if (timeline is Resource.Success) {
                TimeLine(timeline.data, sourceId = viewModel.sourceId)
            }
        }
        if (timeline == Resource.Loading) {
            Loading()
        } else if (timeline is Resource.Error) {
            ErrorTip(message = timeline.message) {
                viewModel.loadData()
            }
        }
    }
}

@Composable
fun TimeLine(data: UpdateTimeLine, sourceId: String) {
    val defaultFocusRequester = remember {
        FocusRequester()
    }
    val rowState = rememberTvLazyListState(data.current)
    TvLazyRow(
        state = rowState,
        content = {
        items(count = data.timeline.size, key = { data.timeline[it].first }) { idx ->
            val timeline = data.timeline[idx]
            TimeLineColumn(
                modifier = Modifier.width(200.dp).run {
                    if (idx == data.current) {
                        focusRequester(defaultFocusRequester)
                    } else {
                        this
                    }
                },
                name = timeline.first,
                animeList = timeline.second,
                sourceId = sourceId
            )
        }
    })
    LaunchedEffect(Unit) {
        rowState.scrollToItem(data.current)
        try {
            defaultFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun TimeLineColumn(
    modifier: Modifier = Modifier,
    name: String,
    animeList: List<AnimeData>,
    sourceId: String
) {
    val context = LocalContext.current
    FocusGroup(modifier = modifier) {
        Column(
            Modifier
                .padding(horizontal = 10.dp)
                .fillMaxSize()
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            TvLazyColumn(
                content = {
                items(count = animeList.size, key = { animeList[it].url }) { idx ->
                    val anime = animeList[idx]
                    AnimeName(
                        modifier = Modifier.run {
                            if (idx == 0) {
                                initiallyFocused()
                            } else {
                                restorableFocus()
                            }
                                .padding(vertical = 2.dp)
                        },
                        name = anime.title
                    ) {
                        DetailActivity.startActivity(context, anime.id, sourceId)
                    }
                }
            })
        }
    }

}


@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AnimeName(
    modifier: Modifier = Modifier, name: String, onClick: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = modifier.onFocusChanged {
            focused = it.hasFocus || it.isFocused
        },
        onClick = onClick,
        scale = ClickableSurfaceScale.None,
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.border))
        ),
        colors = ClickableSurfaceDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp, 4.dp).run {
                if (focused) {
                    basicMarquee()
                } else {
                    this
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }

}