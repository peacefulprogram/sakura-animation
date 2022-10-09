package com.jing.sakura.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.jing.sakura.R
import com.jing.sakura.databinding.LoadVideoUrlFramentBinding
import com.jing.sakura.detail.AnimeDetailFragmentDirections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoadVideoUrlFragment : Fragment() {
    private lateinit var episodeUrl: String
    private lateinit var animeTitle: String
    private lateinit var viewBinding: LoadVideoUrlFramentBinding

    private lateinit var navController: NavController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
        LoadVideoUrlFragmentArgs.fromBundle(requireArguments()).let {
            episodeUrl = it.episodeUrl
            animeTitle = it.animeTitle
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewBinding =
            LoadVideoUrlFramentBinding.inflate(inflater, container, false)
        viewBinding.root.run {
            requestFocus()
            setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        findNavController().popBackStack()
                        true
                    }
                    else -> false
                }

            }
        }
        viewBinding.loadHint.text = "正在加载视频链接: $animeTitle"
        val webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.url?.takeIf { it.path?.endsWith(".m3u8") ?: false }?.let {
                    onFoundVideoUrl(it.toString(), webView = viewBinding.webView)
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        viewBinding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            this.webViewClient = webViewClient
            loadUrl(episodeUrl)
        }
        return viewBinding.root
    }

    private fun onFoundVideoUrl(url: String, webView: WebView) {
        lifecycleScope.launch(Dispatchers.Main) {
            webView.run {
                clearHistory()
                clearCache(true)
                loadUrl("about:blank")
                onPause()
                removeAllViews()
                pauseTimers()
                destroy()
            }
            navController.navigate(
                LoadVideoUrlFragmentDirections.actionLoadVideoUrlFragmentToAnimePlayerFragment(
                    url,
                    animeTitle
                ),
                navOptions {
                    popUpTo(R.id.animeDetailFragment) {
                        inclusive = false
                    }
                }
            )
        }

    }

}