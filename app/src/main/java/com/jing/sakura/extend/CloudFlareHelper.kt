package com.jing.sakura.extend

import okhttp3.Response

object CloudFlareHelper : WebViewCookieHelper() {

    private val ERROR_CODES = arrayOf(403, 503)

    private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")

    override val cookieName: String
        get() = "cf_clearance"
    override val notice: String = "正在尝试通过CloudFlare检测,请耐心等待"
    override val timeoutSeconds: Long
        get() = 60

    override fun shouldIntercept(response: Response): Boolean {
        return response.code in ERROR_CODES && response.header("server") in SERVER_CHECK
    }

}