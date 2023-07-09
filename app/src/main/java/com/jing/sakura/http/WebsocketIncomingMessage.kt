package com.jing.sakura.http

data class WebsocketIncomingMessage(
    val operation: String,
    val content: String? = null
)

enum class WebsocketOperation {
    INPUT, SUBMIT, PING
}