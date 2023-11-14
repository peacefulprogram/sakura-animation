package com.jing.sakura.data

sealed class Resource<in T> {
    data class Success<T>(val data: T) : Resource<T>()

    data class Error<T>(val message: String) : Resource<T>()

    data class Loading(val silent: Boolean = false) : Resource<Any>()

    companion object {
        val Loading = Loading(silent = false)
    }
}
