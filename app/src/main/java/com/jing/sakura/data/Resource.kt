package com.jing.sakura.data

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()

    data class Error(val message: String) : Resource<Nothing>()

    data class Loading(val silent: Boolean = false) : Resource<Nothing>()

    fun getOrNull(): T? {
        if (this is Success) {
            return data
        }
        return null
    }

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading


    companion object {
        val Loading = Loading(silent = false)
    }
}
