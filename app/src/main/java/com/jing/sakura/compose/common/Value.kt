package com.jing.sakura.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.reflect.KProperty

data class Value<T>(
    var value: T

)

operator fun <T> Value<T>.getValue(
    thisObj: Any?,
    property: KProperty<*>
): T {
    return this.value
}

operator fun <T> Value<T>.setValue(
    thisObj: Any?,
    property: KProperty<*>,
    newValue: T
) {
    this.value = newValue
}

