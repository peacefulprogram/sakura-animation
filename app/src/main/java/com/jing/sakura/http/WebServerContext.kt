package com.jing.sakura.http

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.internal.closeQuietly
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

object WebServerContext {

    lateinit var androidContext: android.app.Application

    var handlerFragment: WebsocketFrameAwareFragment? = null


    lateinit var hostIp: String

    var server: ApplicationEngine? = null

    var serverPort: Int? = null

    init {
        GlobalScope.launch(Dispatchers.IO) {
            hostIp = findIp()
            serverPort = findPort()
            server = if (serverPort == null) {
                null
            } else {
                embeddedServer(Jetty, serverPort!!) {
                    install(WebSockets) {
                        contentConverter = JacksonWebsocketContentConverter()
                    }
                    initRouter()
                }.start(false)
            }
        }
    }

    fun registerFragment(fragment: WebsocketFrameAwareFragment): Unit {
        handlerFragment = fragment
    }

    fun removeFragment(fragment: WebsocketFrameAwareFragment?) {
        if (handlerFragment == fragment) {
            handlerFragment = null
        }
    }

    private fun Application.initRouter() {
        routing {

            get("/") {
                val bytes = androidContext.assets.open("input.html").readBytes()
                call.respondBytes(bytes = bytes, contentType = ContentType.parse("text/html"))
            }

            webSocket("/input") {
                if (handlerFragment == null) {
                    sendSerialized(WebsocketResponse(false, "无页面监听"))
                }

                while (true) {
                    val message = receiveDeserialized<WebsocketIncomingMessage>()
                    val operationType = WebsocketOperation.valueOf(message.operation)
                    if (handlerFragment == null) {
                        sendSerialized(WebsocketResponse(false, "无页面监听"))
                        continue
                    }
                    when (val result =
                        handlerFragment!!.onMessage(operationType, message.content)) {
                        is WebsocketResult.Success -> sendSerialized(WebsocketResponse(true))
                        is WebsocketResult.Error -> sendSerialized(
                            WebsocketResponse(
                                false,
                                result.message
                            )
                        )
                        is WebsocketResult.Close -> close(
                            CloseReason(
                                CloseReason.Codes.CANNOT_ACCEPT,
                                result.message
                            )
                        )
                    }
                }
            }
        }
    }

    private data class WebsocketResponse(val success: Boolean, val message: String = "")


    private fun findPort(): Int? {
        for (port in 8080..10000) {
            try {
                ServerSocket(port).apply {
                    closeQuietly()
                }
                return port
            } catch (e: Exception) {

            }
        }
        return null
    }

    private fun findIp(): String {
        var candidate: InetAddress? = null
        val result = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .find {
                if (candidate == null && !it.isLoopbackAddress) {
                    candidate = it
                }
                it.isSiteLocalAddress
            }
        return (result ?: candidate ?: InetAddress.getLocalHost()).hostAddress!!
    }
}