package com.jing.sakura.http

data class WebsocketIncomingMessage(
    val operation: String,
    val content: String = ""
)

enum class WebsocketOperation {
    INPUT, SUBMIT
}