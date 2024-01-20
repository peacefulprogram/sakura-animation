package com.jing.sakura.compose.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.customClick(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
): Modifier {
    if (onClick == null && onLongClick == null) {
        return this
    }
    var keyDownCount by Value(0)
    return onPreviewKeyEvent { keyEvent ->
        // 自定义click和long click
        if (keyEvent.key == Key.DirectionCenter) {
            // 长按时会触发多次key down event
            if (keyEvent.type == KeyEventType.KeyDown) {
                keyDownCount++
            } else {
                if (keyDownCount > 1) {
                    (onLongClick ?: onClick)?.invoke()
                } else {
                    onClick?.invoke()
                }
                keyDownCount = 0
            }
            true
        } else {
            false
        }
    }
}