package com.jing.sakura.extend

import okhttp3.Response

object FunCDNHelper : WebViewCookieHelper() {
    override val cookieName: String
        get() = "_funcdn_token"
    override val notice: String
        get() = "正在通过FunCDN检测,请耐心等待"
    override val timeoutSeconds: Long
        get() = 30

    override fun shouldIntercept(response: Response): Boolean {
        return response.code == 512 && response.header("Server")?.startsWith("FunCDN") == true
    }
}