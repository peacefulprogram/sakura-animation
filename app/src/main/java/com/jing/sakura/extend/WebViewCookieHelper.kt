package com.jing.sakura.extend

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewClientCompat
import com.jing.sakura.BuildConfig
import com.jing.sakura.SakuraApplication
import okhttp3.Cookie
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class WebViewCookieHelper {

    abstract val cookieName: String

    abstract val notice: String?

    abstract val timeoutSeconds: Long

    abstract fun shouldIntercept(response: Response): Boolean

    fun obtainCookieThroughWebView(originalRequest: Request): Boolean {

        val latch = CountDownLatch(1)

        var webview: WebView? = null

        val origRequestUrl = originalRequest.url.toString()
        val cfCookie: ObjectRef<Cookie?> = ObjectRef(null)
        mainExecutor.execute {
            notice?.let {
                SakuraApplication.context.showLongToast(it)
            }
            webview = createWebView(originalRequest)

            webview?.webViewClient = object : WebViewClientCompat() {

                override fun onPageFinished(view: WebView, url: String) {

                    val cookieStr = cookieManager.getCookie(origRequestUrl)
                    if (cookieStr?.isNotEmpty() == true) {
                        cookieStr.split(';')
                            .map { Cookie.parse(originalRequest.url, it) }
                            .find { it?.name == cookieName }
                            ?.let { cfCookie.offer(it) }
                    }
                    if (url == origRequestUrl && cfCookie.get() != null) {
                        latch.countDown()
                    }
                }
            }

            webview?.loadUrl(origRequestUrl)
        }

        latch.await(timeoutSeconds, TimeUnit.SECONDS)

        mainExecutor.execute {

            webview?.run {
                stopLoading()
                destroy()
            }
            if (BuildConfig.DEBUG) {
                SakuraApplication.context.showLongToast("cookie: ${cfCookie.get()}")
            }
        }
        return cfCookie.get() != null
    }


    private fun createWebView(request: Request): WebView {
        return WebView(SakuraApplication.context).apply {
            setDefaultSettings()
            settings.userAgentString = request.header("user-agent") ?: SakuraApplication.USER_AGENT
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.setDefaultSettings() {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT

            // Allow zooming
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
    }

    private class ObjectRef<T>(initialValue: T) {
        @Volatile
        private var value: T = initialValue

        fun offer(value: T) {
            this.value = value
        }

        fun get(): T {
            return value
        }
    }

    companion object {
        private val mainExecutor by lazy { ContextCompat.getMainExecutor(SakuraApplication.context) }


        private val cookieManager by lazy {
            CookieManager.getInstance().apply { setAcceptCookie(true) }
        }

    }
}