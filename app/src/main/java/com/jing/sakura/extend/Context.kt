package com.jing.sakura.extend

import android.content.Context
import android.graphics.Color
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

fun Context.showLongToast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

fun Context.showShortToast(text: CharSequence) =
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()


fun Context.getColorWithAlpha(@ColorRes resId: Int, alpha: Float) =
    Color.valueOf(ContextCompat.getColor(this, resId)).run {
        Color.valueOf(red(), green(), blue(), alpha)
    }