package com.jing.sakura.compose.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: @Composable () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }
    var focusText by remember(focused) {
        mutableStateOf(false)
    }
    Box(
        modifier
            .onFocusChanged {
                focused = it.hasFocus || it.isFocused
            }
            .clickable(onClick = {
                focusText = true
            })
            .run {
                if (focused) {
                    border(
                        1.dp,
                        color = MaterialTheme.colorScheme.border,
                        shape = MaterialTheme.shapes.small
                    )
                } else {
                    this
                }
            }
            .focusable()
    ) {
        val focusRequester = remember {
            FocusRequester()
        }
        TextField(
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = androidx.compose.material3.MaterialTheme.shapes.small,
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
            placeholder = placeholder
        )
        LaunchedEffect(focusText) {
            if (focusText) {
                focusRequester.safelyRequestFocus()
            }
        }
    }
}