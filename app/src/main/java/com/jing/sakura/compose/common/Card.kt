package com.jing.sakura.compose.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    modifier: Modifier = Modifier,
    imageUrl: String,
    title: String,
    subTitle: String = "",
    sourceName: String = "",
    focusScale: Float = 1.1f,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    var focused by remember {
        mutableStateOf(false)
    }
    var keyDownCount by remember(focused) {
        Value(0)
    }
    CompactCard(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .customClick(onClick, onLongClick),
        onClick = {},
        image = {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        },
        scale = CardDefaults.scale(focusedScale = focusScale),
        title = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            ) {
                if (sourceName.isNotEmpty()) {
                    Text(text = sourceName, maxLines = 1)
                }
                Text(text = title, maxLines = 1, modifier = Modifier.run {
                    if (focused) {
                        basicMarquee()
                    } else {
                        this
                    }
                })
                if (subTitle.isNotEmpty()) {
                    Text(text = subTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier
                            .graphicsLayer { alpha = 0.7f }
                            .run {
                                if (focused) {
                                    basicMarquee()
                                } else {
                                    this
                                }
                            })

                }
            }
        }

    )
}