package com.jing.sakura.http

fun interface WsMessageHandler {

    fun onMessage(operation: WebsocketOperation, content: String): WebsocketResult
}