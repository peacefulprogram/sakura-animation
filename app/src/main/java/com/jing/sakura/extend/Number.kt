package com.jing.sakura.extend

import android.content.Context
import android.util.DisplayMetrics

fun Number.dpToPixels(context: Context): Float =
    this.toFloat() * context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT

fun Number.secondsToMinuteAndSecondText(): String {
    val seconds = this.toLong()
    if (seconds <= 0) {
        return "00:00"
    }
    val secondPart = seconds % 60
    val minutes = seconds.div(60)
    val result = StringBuilder()
    if (minutes < 10) {
        result.append(0)
    }
    result.append(minutes)
    result.append(':')
    if (secondPart < 10) {
        result.append(0)
    }
    result.append(secondPart)
    return result.toString()
}