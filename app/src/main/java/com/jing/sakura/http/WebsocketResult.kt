package com.jing.sakura.http

sealed class WebsocketResult {
    object Success : WebsocketResult()
    data class Close(val message: String) : WebsocketResult()
    data class Error(val message: String) : WebsocketResult()

}
