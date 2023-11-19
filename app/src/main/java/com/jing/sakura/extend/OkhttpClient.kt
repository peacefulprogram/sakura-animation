package com.jing.sakura.extend

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun OkHttpClient.getHtml(url: String, requestHandler: Request.Builder.() -> Unit = {}): String {
    val req = Request.Builder()
        .url(url)
        .get()
        .run {
            requestHandler()
            this
        }
        .build()
    val resp = newCall(req).execute()
    if (resp.code != 200) {
        throw RuntimeException("请求${url}错误,code: " + resp.code)
    }
    return resp.body?.string() ?: throw RuntimeException("${url}响应体为空")
}


fun OkHttpClient.getDocument(
    url: String,
    requestHandler: Request.Builder.() -> Unit = {}
): Document =
    Jsoup.parse(getHtml(url, requestHandler = requestHandler)).apply { setBaseUri(url) }