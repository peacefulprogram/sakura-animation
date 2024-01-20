package com.jing.sakura.compose.common

import android.util.Log
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester


data class UpAndDownFocusProperties(
    val up: FocusRequester? = null,
    val down: FocusRequester? = null
) {
    companion object {
        val DEFAULT = UpAndDownFocusProperties()
    }
}

fun FocusProperties.applyUpAndDown(prop: UpAndDownFocusProperties) {
    if (prop.up != null) {
        up = prop.up
    }
    if (prop.down != null) {
        down = prop.down
    }
}

fun FocusRequester.safelyRequestFocus(label: String = "") {
    try {
        requestFocus()
    } catch (ex: Exception) {
        Log.d("FocusRequester", "$label requestFocus fail: $${ex.message}", ex)
    }
}