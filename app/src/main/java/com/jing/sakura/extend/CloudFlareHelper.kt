package com.jing.sakura.extend

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import com.jing.sakura.BuildConfig
import com.jing.sakura.SakuraApplication
import okhttp3.Cookie
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object CloudFlareHelper {

    private val mainExecutor by lazy { ContextCompat.getMainExecutor(SakuraApplication.context) }

    private val cookieManager by lazy {
        CookieManager.getInstance().apply { setAcceptCookie(true) }
    }

    const val CLOUD_FLARE_COOKIE_NAME = "cf_clearance"

    private val ERROR_CODES = arrayOf(403, 503)

    private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    fun shouldProcessCloudflare(response: Response): Boolean {
        return response.code in ERROR_CODES && response.header("server") in SERVER_CHECK
    }

    fun passCloudFlareCheck(originalRequest: Request, timeoutSeconds: Int = 60): Cookie? {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null

        val challengeFound = ObjectRef(false)

        val origRequestUrl = originalRequest.url.toString()
        val cfCookie: ObjectRef<Cookie?> = ObjectRef(null)
        mainExecutor.execute {
            SakuraApplication.context.showLongToast("正在通过CloudFlare检测,请耐心等待")
            webview = createWebView(originalRequest)

            webview?.webViewClient = object : WebViewClientCompat() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    if (BuildConfig.DEBUG && request.url.toString() == origRequestUrl) {
                        Log.e("CloudFlare", "shouldInterceptRequest: ${request.url}")
                        request.requestHeaders.forEach { (k, v) ->
                            Log.e("CloudFlare", "shouldInterceptRequest: $k=$v")
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String) {

                    val cookieStr = cookieManager.getCookie(origRequestUrl)
                    if (cookieStr?.isNotEmpty() == true) {
                        cookieStr.split(';')
                            .map { Cookie.parse(originalRequest.url, it) }
                            .find { it?.name == CLOUD_FLARE_COOKIE_NAME }
                            ?.let { cfCookie.offer(it) }
                    }
                    if (cfCookie.get() != null) {
                        latch.countDown()

                    }
                    if (url == origRequestUrl && challengeFound.get()) {
                        // The first request didn't return the challenge, abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceErrorCompat
                ) {
                    onReceivedErrorCompat(
                        view,
                        error.errorCode,
                        error.description?.toString(),
                        request.url.toString(),
                        request.isForMainFrame,
                    )
                }


                @Deprecated("Deprecated in Java")
                final override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String,
                ) {
                    onReceivedErrorCompat(
                        view,
                        errorCode,
                        description,
                        failingUrl,
                        failingUrl == view.url
                    )
                }

                final override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceResponse,
                ) {
                    onReceivedErrorCompat(
                        view,
                        error.statusCode,
                        error.reasonPhrase,
                        request.url
                            .toString(),
                        request.isForMainFrame,
                    )
                }

                fun onReceivedErrorCompat(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String,
                    isMainFrame: Boolean,
                ) {
                    if (isMainFrame) {
                        if (errorCode in ERROR_CODES) {
                            // Found the Cloudflare challenge page.
//                            challengeFound.offer(true)
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }
            }

            webview?.loadUrl(origRequestUrl)
        }

        latch.await(timeoutSeconds.toLong(), TimeUnit.SECONDS)

        mainExecutor.execute {

            webview?.run {
                stopLoading()
                destroy()
            }
            if (BuildConfig.DEBUG) {
                SakuraApplication.context.showLongToast("cf_cookie: ${cfCookie.get()}")
            }
        }
        return cfCookie.get()
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
}