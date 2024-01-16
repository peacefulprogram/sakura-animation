package com.jing.sakura.extend

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewClientCompat
import com.jing.sakura.BuildConfig
import com.jing.sakura.SakuraApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

abstract class WebViewCookieHelper {

    abstract val cookieName: String

    abstract val notice: String?

    abstract val timeoutSeconds: Long

    abstract fun shouldIntercept(response: Response): Boolean

    suspend fun obtainCookieThroughWebView(originalRequest: Request) {
        val origRequestUrl = originalRequest.url.toString()
        val cookieDef = CompletableDeferred<Unit>()
        cookieManager.removeCookies(origRequestUrl)
        val webView = withContext(Dispatchers.Main) {
            notice?.let {
                SakuraApplication.context.showLongToast(it)
            }
            createWebView(originalRequest).apply {
                webViewClient = object : WebViewClientCompat() {

                    override fun onPageFinished(view: WebView?, url: String) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "onPageFinished: $url")
                        }
                        if (url != origRequestUrl) {
                            return
                        }
                        val cookieStr = cookieManager.getCookie(origRequestUrl)
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "onPageFinished: $cookieStr")
                        }
                        if (cookieStr?.isNotEmpty() == true) {
                            val found = cookieStr.split(";")
                                .any {
                                    val ck = Cookie.parse(originalRequest.url, it)
                                    ck != null && ck.name == cookieName && ck.value.isNotEmpty()
                                }
                            if (found) {
                                cookieDef.complete(Unit)
                            }
                        }
                    }
                }
                loadUrl(origRequestUrl)
            }
        }
        try {
            withTimeout(timeoutSeconds.seconds) {
                cookieDef.await()
            }
        } catch (ex: TimeoutCancellationException) {
            throw RuntimeException(ex.message)
        } finally {
            mainExecutor.execute {
                with(webView) {
                    stopLoading()
                    destroy()
                }
            }
        }
    }


    private fun createWebView(request: Request): WebView {
        return WebView(SakuraApplication.context).apply {
            setDefaultSettings()
            settings.userAgentString = request.header("user-agent") ?: SakuraApplication.USER_AGENT
        }
    }


    private suspend fun CookieManager.removeCookies(url: String) {
        val cookieStr = getCookie(url)?.takeIf { it.isNotEmpty() } ?: return
        val httpUrl = url.toHttpUrl()
        val cookies = cookieStr.split(";")
            .asSequence()
            .map { Cookie.parse(httpUrl, it)?.name }
            .filterNotNull()
            .map { "$it=; Max-Age=0" }
            .toList()
        val defList = List(cookies.size) { CompletableDeferred<Unit>() }
        withContext(Dispatchers.Main) {
            cookies.forEachIndexed { index, ck ->
                setCookie(httpUrl.host, ck) {
                    defList[index].complete(Unit)
                }
            }
            flush()
        }
        defList.awaitAll()
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

    companion object {
        private val mainExecutor by lazy { ContextCompat.getMainExecutor(SakuraApplication.context) }

        private const val TAG = "WebViewCookieHelper"

        private val cookieManager by lazy {
            CookieManager.getInstance().apply { setAcceptCookie(true) }
        }

    }
}