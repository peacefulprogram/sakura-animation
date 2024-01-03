package com.jing.sakura.extend

import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.Charset

fun OkHttpClient.newRequest(
    bypassCloudFlare: Boolean = false,
    block: Request.Builder.() -> Unit
): Response {
    val req = Request.Builder()
        .apply(block)
        .build()
    val resp = newCall(req).execute()
    if (!bypassCloudFlare || !CloudFlareHelper.shouldProcessCloudflare(resp)) {
        return resp
    }
    resp.close()
    val cookieJar = this.cookieJar
    // 删除已有cookie
    if (cookieJar is AndroidCookieJar) {
        cookieJar.remove(req.url, listOf(CloudFlareHelper.CLOUD_FLARE_COOKIE_NAME))
    } else {
        val oldCookies = cookieJar.loadForRequest(req.url)
        if (oldCookies.isNotEmpty()) {
            oldCookies.map {
                Cookie.Builder()
                    .domain(it.domain)
                    .name(it.name)
                    .path(it.path)
                    .expiresAt(0L)
                    .build()
            }.let { cookieJar.saveFromResponse(req.url, it) }
        }
    }
    val cfCookie = try {
        CloudFlareHelper.passCloudFlareCheck(resp.request)
    } catch (ex: Exception) {
        Log.e("OkHttpClient.newRequest", "cloud flare检测错误, ${ex.message}", ex)
        null
    }
    cfCookie ?: throw RuntimeException("cloud flare 检测失败")
    if (cookieJar !is AndroidCookieJar) {
        cookieJar.saveFromResponse(req.url, listOf(cfCookie))
    }
    return newCall(Request.Builder().apply(block).build()).execute()
}


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


class AndroidCookieJar : CookieJar {
    private val manager = CookieManager.getInstance().apply {
        setAcceptCookie(true)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return get(url)
    }

    fun get(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())
        return if (cookies != null && cookies.isNotEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it) }
        } else {
            emptyList()
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        return cookies.split(";")
            .map { it.substringBefore("=") }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
            .count()
    }

    fun put(url: String, vararg cookies: Cookie) {
        val urlString = url.toHttpUrl().toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    fun removeAll() {
        manager.removeAllCookies {}
    }

    fun flush() {
        manager.flush()
    }
}
