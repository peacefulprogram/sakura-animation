package com.jing.sakura.extend

import android.graphics.Color
import kotlin.math.roundToInt

fun Int.getColorWithAlpha(ratio: Float): Int {
    return Color.argb(
        (Color.alpha(this) * ratio).roundToInt(),
        Color.red(this),
        Color.green(this),
        Color.blue(this)
    )
}