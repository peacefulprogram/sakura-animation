package com.jing.sakura.compose.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private val ClickKeys = intArrayOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER
)
fun Modifier.customClick(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null
): Modifier {
    if (onClick == null && onLongClick == null) {
        return this
    }
    var keyDownCount by Value(0)
    return onPreviewKeyEvent { keyEvent ->
        if (onKeyEvent?.invoke(keyEvent) == true) {
            return@onPreviewKeyEvent true
        }
        // 自定义click和long click
        if (keyEvent.nativeKeyEvent.keyCode in ClickKeys) {
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