package com.jing.sakura.extend

import android.content.Context
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

fun Context.showLongToast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

fun Context.showShortToast(text: CharSequence) =
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()


fun Context.getColorWithAlpha(@ColorRes resId: Int, alpha: Float) =
    ContextCompat.getColor(this, resId).run {
        ColorUtils.setAlphaComponent(this, (alpha * 0xff).toInt())
    }