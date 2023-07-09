package com.jing.sakura.http

import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.jing.sakura.SakuraApplication
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

object WebServerContext {

    private val TAG = WebServerContext::class.java.simpleName

    @Volatile
    var server: NanoWSD? = null

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String>
        get() = _serverUrl

    private val gson = Gson()

    @Volatile
    var messageHandler: WsMessageHandler? = null
        private set

    init {
        GlobalScope.launch(Dispatchers.IO) {
            val hostIp = findIp()
            val port = findPort()
            server = if (port == null) {
                null
            } else {
                _serverUrl.emit("http://$hostIp:$port")
                WsServer(port).apply {
                    start(20_000)
                }
            }
        }
    }

    fun registerMessageHandler(handler: WsMessageHandler) {
        messageHandler = handler
    }

    fun unregisterMessageHandler(handler: WsMessageHandler) {
        if (messageHandler == handler) {
            messageHandler = null
        }
    }

    @Keep
    private data class WebsocketResponse(val success: Boolean, val message: String = "")


    private fun findPort(): Int? {
        for (port in 9999..60000) {
            try {
                ServerSocket(port).apply {
                    closeQuietly()
                }
                return port
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun findIp(): String {
        var candidate: InetAddress? = null
        val result = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }.find {
                if (candidate == null && !it.isLoopbackAddress) {
                    candidate = it
                }
                it.isSiteLocalAddress && it.hostAddress?.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) == true
            }
        return (result ?: candidate ?: InetAddress.getLocalHost()).hostAddress!!
    }

    class WsServer(port: Int) : NanoWSD(port) {
        override fun openWebSocket(handshake: IHTTPSession): WebSocket = WsHandler(handshake)

        override fun serveHttp(session: IHTTPSession?): Response {

            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK,
                NanoHTTPD.MIME_HTML,
                SakuraApplication.context.assets.open("input.html").use {
                    it.readBytes().toString(Charsets.UTF_8)
                })
        }

    }

    class WsHandler(session: NanoHTTPD.IHTTPSession) : WebSocket(session) {
        override fun onOpen() {
        }

        override fun onClose(
            code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean
        ) {
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            try {
                handleIncomingMessage(message)
            } catch (ex: Exception) {
                Log.e(TAG, "handle ws message error: ${ex.message}", ex)
                close(NanoWSD.WebSocketFrame.CloseCode.InternalServerError, ex.message, false)
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame?) {
        }

        override fun onException(exception: IOException) {
            Log.e(TAG, "websocket io error:${exception.message}", exception)
            close(
                NanoWSD.WebSocketFrame.CloseCode.InternalServerError, exception.message, false
            )
        }

    }

    private fun WebSocket.handleIncomingMessage(wsFrame: NanoWSD.WebSocketFrame) {
        if (messageHandler == null) {
            sendJson(WebsocketResponse(false, "无监听输入框"))
            return
        }
        val payload = wsFrame.textPayload
        val message = gson.fromJson(payload, WebsocketIncomingMessage::class.java)
        val operation = when (message.operation.uppercase()) {
            "INPUT" -> WebsocketOperation.INPUT
            "SUBMIT" -> WebsocketOperation.SUBMIT
            "PING" -> WebsocketOperation.PING
            else -> throw IllegalArgumentException("invalid operation${message.operation}")
        }
        if (operation == WebsocketOperation.PING) {
            send(gson.toJson(WebsocketResponse(true)))
            return
        }
        runBlocking {
            val result = withContext(Dispatchers.Main) {
                messageHandler!!.onMessage(operation, message.content ?: "")
            }
            when (result) {
                is WebsocketResult.Success -> sendJson(
                    WebsocketResponse(
                        true
                    )
                )

                is WebsocketResult.Error -> sendJson(
                    WebsocketResponse(
                        false, result.message
                    )
                )

                is WebsocketResult.Close -> close(
                    NanoWSD.WebSocketFrame.CloseCode.NormalClosure, result.message, false
                )
            }
        }
    }

    private fun WebSocket.sendJson(data: Any) {
        send(gson.toJson(data))
    }

    fun stopServer() {
        server?.stop()
        server = null
    }
}