package com.jing.sakura.http

import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.internal.closeQuietly
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.*

object WebServerContext {

    lateinit var androidContext: android.app.Application

    private val fragmentIdMap: MutableMap<String, WebsocketFrameAwareFragment> =
        Collections.synchronizedMap(
            mutableMapOf()
        )

    lateinit var hostIp: String


    var server: NettyApplicationEngine? = null

    var serverPort: Int? = null

    init {
        GlobalScope.launch(Dispatchers.IO) {
            hostIp = findIp()
            serverPort = findPort()
            server = if (serverPort == null) {
                null
            } else {
                embeddedServer(Netty, serverPort!!) {
                    install(WebSockets) {
                        contentConverter = KotlinxWebsocketSerializationConverter(Json)
                    }
                    initRouter()
                }.start(false)
            }
        }
    }

    fun registerFragment(fragment: WebsocketFrameAwareFragment): String {
        val id = UUID.randomUUID().toString()
        fragmentIdMap[id] = fragment
        return id
    }

    fun removeFragment(id: String?) {
        fragmentIdMap.remove(id)
    }

    private fun Application.initRouter() {
        routing {

            get("/") {
                val bytes = androidContext.assets.open("input.html").readBytes()
                call.respondBytes(bytes = bytes, contentType = ContentType.parse("text/html"))
            }

            webSocket("/{id}") {
                val id = call.parameters["id"]
                if (!fragmentIdMap.containsKey(id)) {
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "监听页面不存在"))
                    return@webSocket
                }
                for (frame in incoming) {
                    val message = receiveDeserialized<WebsocketIncomingMessage>()
                    val operationType = WebsocketOperation.valueOf(message.operation)
                    if (!fragmentIdMap.containsKey(id)) {
                        close(CloseReason(CloseReason.Codes.GOING_AWAY, "页面已关闭"))
                        return@webSocket
                    }
                    when (val result =
                        fragmentIdMap[id]!!.onMessage(operationType, message.content)) {
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