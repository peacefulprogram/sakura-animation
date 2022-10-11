package com.jing.sakura.extend

import android.content.Context
import android.util.DisplayMetrics

fun Number.dpToPixels(context: Context): Float =
    this.toFloat() * context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT