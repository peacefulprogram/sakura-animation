package com.jing.sakura.compose.screen

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.R
import com.jing.sakura.compose.common.ConfirmDeleteDialog
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.SpeechToTextParser
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.http.WebsocketOperation
import com.jing.sakura.http.WebsocketResult
import com.jing.sakura.http.WsMessageHandler
import com.jing.sakura.room.SearchHistoryEntity
import com.jing.sakura.search.SearchResultActivity
import com.jing.sakura.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(viewModel: SearchViewModel) {

    val context = LocalContext.current
    val onSearch = { keyword: String ->
        if (keyword.isNotBlank()) {
            keyword.trim().let {
                viewModel.saveHistory(it)
                SearchResultActivity.startActivity(context, it, viewModel.sourceId)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        InputKeywordRow(onSearch)

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = spacedBy(10.dp)
            ) {
                val serverUrl = WebServerContext.serverUrl.collectAsState().value
                if (serverUrl.isNotEmpty()) {
                    Text(text = "也可以扫码输入")
                    val img = remember(serverUrl) {
                        val bitMatrix =
                            QRCodeWriter().encode(serverUrl, BarcodeFormat.QR_CODE, 512, 512)
                        val bitmap = Bitmap.createBitmap(
                            bitMatrix.width,
                            bitMatrix.height,
                            Bitmap.Config.RGB_565
                        )

                        for (x in 0 until bitMatrix.width) {
                            for (y in 0 until bitMatrix.height) {
                                bitmap.setPixel(
                                    x,
                                    y,
                                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                                )
                            }
                        }
                        bitmap
                    }

                    AsyncImage(model = img, contentDescription = "qr code")
                }

            }

            val searchHistory = viewModel.searchHistoryPager.collectAsLazyPagingItems()

            if (searchHistory.loadState.refresh is LoadState.NotLoading && searchHistory.itemCount > 0) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    Text(text = "搜索历史")
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchHistoryColumn(viewModel = viewModel, onKeywordClick = onSearch)

                }
            }

        }

    }

}


@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun InputKeywordRow(onSearch: (String) -> Unit) {
    val speechFocusRequester = remember {
        FocusRequester()
    }
    val context = LocalContext.current
    val speechToTextParser = remember {
        SpeechToTextParser(context)
    }
    val permissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO) {
        if (it) {
            speechToTextParser.startListening()
        }
    }
    var inputKeyword by remember {
        mutableStateOf("")
    }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val handler = WsMessageHandler { operation, content ->
            coroutineScope.launch(Dispatchers.Main) {
                when (operation) {
                    WebsocketOperation.INPUT -> inputKeyword = content
                    WebsocketOperation.SUBMIT -> onSearch(inputKeyword)
                    else -> {}
                }
            }
            WebsocketResult.Success
        }
        WebServerContext.registerMessageHandler(handler)

        onDispose {
            WebServerContext.unregisterMessageHandler(handler)
        }

    }

    val searchButtonFocusRequester = remember {
        FocusRequester()
    }
    val sttState by speechToTextParser.state.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(targetState = sttState.isSpeaking, label = "") { isSpeaking ->
            IconButton(
                onClick = {
                    if (isSpeaking) {
                        speechToTextParser.stopListening()
                    } else {
                        if (permissionState.status.isGranted) {
                            speechToTextParser.startListening()
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    }
                },
                scale = ButtonScale.None,
                modifier = Modifier.focusRequester(speechFocusRequester)
            ) {
                if (isSpeaking) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        tint = colorResource(id = R.color.red400),
                        contentDescription = "stop"
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Mic, contentDescription = "speak"
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        TextField(value = inputKeyword,
            onValueChange = { inputKeyword = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                if (sttState.isSpeaking) {
                    Text(text = stringResource(R.string.speak_search_keyword))
                } else {
                    Text(text = stringResource(R.string.input_search_keyword))
                }
            })
        Spacer(modifier = Modifier.width(20.dp))

        IconButton(
            onClick = {
                onSearch(inputKeyword.trim())
            },
            enabled = inputKeyword.isNotBlank(),
            modifier = Modifier.focusRequester(searchButtonFocusRequester)
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "search")
        }
    }

    LaunchedEffect(sttState) {
        if (!sttState.isSpeaking && sttState.text.isNotEmpty()) {
            inputKeyword = sttState.text.trim()
            if (inputKeyword.isNotBlank()) {
                searchButtonFocusRequester.requestFocus()
            }
        }
    }
    LaunchedEffect(sttState.isSpeaking) {
        speechFocusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun SearchHistoryColumn(
    viewModel: SearchViewModel,
    onKeywordClick: (keyword: String) -> Unit = {}
) {
    val pagingItems = viewModel.searchHistoryPager.collectAsLazyPagingItems()
    if (pagingItems.loadState.refresh !is LoadState.NotLoading || pagingItems.itemCount == 0) {
        return
    }
    var confirmDeleteHistory by remember {
        mutableStateOf<SearchHistoryEntity?>(null)
    }
    val coroutineScope = rememberCoroutineScope()

    val listState = rememberTvLazyListState()
    FocusGroup {
        TvLazyColumn(
            state = listState, content = {
                items(pagingItems.itemCount, key = { pagingItems[it]?.keyword ?: it }) { kwIndex ->
                    val history = pagingItems[kwIndex] ?: return@items
                    Keyword(text = history.keyword,
                        modifier = Modifier
                            .run {
                                if (kwIndex == 0) {
                                    initiallyFocused()
                                } else {
                                    restorableFocus()
                                }
                            }
                            .padding(vertical = 1.dp),
                        onLongClick = {
                            confirmDeleteHistory = history
                        }) {
                        onKeywordClick(history.keyword)
                    }
                }
            }, verticalArrangement = spacedBy(10.dp)
        )
    }

    val history = confirmDeleteHistory ?: return

    val confirmText = String.format(
        stringResource(
            id = R.string.confirm_delete_template
        ), confirmDeleteHistory?.keyword
    )
    ConfirmDeleteDialog(
        text = confirmText,
        onDeleteClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteHistory(history.keyword)
                pagingItems.refresh()
            }
        },
        onDeleteAllClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteAllHistory()
                pagingItems.refresh()
            }
        },
        onCancel = {
            confirmDeleteHistory = null
        }
    )
}


@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Keyword(
    text: String,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }
    Surface(onClick = onClick,
        onLongClick = onLongClick,
        scale = ClickableSurfaceScale.None,
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(
                    2.dp, MaterialTheme.colorScheme.border
                )
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.onFocusChanged {
            focused = it.isFocused || it.hasFocus
        }) {
        var textModifier = Modifier.padding(8.dp, 4.dp)
        if (focused) {
            textModifier = textModifier.basicMarquee()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = textModifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
