package com.jing.sakura.extend

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.Charset

fun OkHttpClient.getHtml(url: String, requestHandler: Request.Builder.() -> Unit = {}): String =
    newRequest {
        get()
        url(url)
        requestHandler()
    }.bodyString()


fun OkHttpClient.getDocument(
    url: String,
    requestHandler: Request.Builder.() -> Unit = {}
): Document = newRequest {
    get()
    url(url)
    requestHandler()
}.asDocument()


fun OkHttpClient.newRequest(block: Request.Builder.() -> Unit): Response {
    val req = Request.Builder()
        .apply(block)
        .build()
    return newCall(req).execute()
}

fun OkHttpClient.newGetRequest(block: Request.Builder.() -> Unit): Response {
    return newRequest {
        block()
        get()
    }
}

fun Response.asDocument(): Document {
    return Jsoup.parse(this.bodyString()).apply {
        setBaseUri(request.url.toString())
    }
}

fun Response.bodyString(charset: Charset = Charsets.UTF_8): String {
    if (code != 200) {
        throw RuntimeException("请求${request.url}失败,code: $code")
    }
    return this.use {
        val body = it.body ?: throw RuntimeException("请求${request.url}失败,响应体为空")
        body.string()
    }
}