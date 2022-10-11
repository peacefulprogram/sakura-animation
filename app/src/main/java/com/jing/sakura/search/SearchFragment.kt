package com.jing.sakura.search

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.databinding.SearchFragmentBinding
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.http.WebsocketFrameAwareFragment
import com.jing.sakura.http.WebsocketOperation
import com.jing.sakura.http.WebsocketResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchFragment : WebsocketFrameAwareFragment() {

    private lateinit var randomId: String

    private lateinit var viewBinding: SearchFragmentBinding
    override fun onMessage(operation: WebsocketOperation, content: String): WebsocketResult {
//        viewBinding.keywordEditor.text.

        val success = when (operation) {
            WebsocketOperation.SUBMIT -> submitText()
            WebsocketOperation.INPUT -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    viewBinding.keywordEditor.setText(
                        content,
                        TextView.BufferType.EDITABLE
                    )
                }
                true
            }
        }
        return if (success) WebsocketResult.Success else WebsocketResult.Error("操作失败")
    }


    private fun submitText(): Boolean {
        val kw = viewBinding.keywordEditor.text.toString().trim()
        if (kw.isEmpty()) {
            Toast.makeText(requireContext(), "关键词不能为空", Toast.LENGTH_SHORT).show()
            return false
        }
        lifecycleScope.launch(Dispatchers.Main) {
            // todo: 导航到搜索页
            findNavController().navigate(
                SearchFragmentDirections.actionSearchFragmentToSearchResultFragment(
                    kw
                )
            )
        }
        return true
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                randomId = WebServerContext.registerFragment(this@SearchFragment)
                val url =
                    "http://${WebServerContext.hostIp}:${WebServerContext.serverPort}?id=$randomId"
                launch(Dispatchers.IO) {
                    val bitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512)
                    val bitmap = Bitmap.createBitmap(
                        bitMatrix.width,
                        bitMatrix.height,
                        Bitmap.Config.RGB_565
                    )

                    for (x in 0 until bitMatrix.width) {
                        for (y in 0 until bitMatrix.height) {
                            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }
                    launch(Dispatchers.Main) {
                        viewBinding.qrcode.setImageBitmap(bitmap)
                    }
                }
            }
            repeatOnLifecycle(Lifecycle.State.DESTROYED) {
                WebServerContext.removeFragment(randomId)
            }

        }
        viewBinding = SearchFragmentBinding.inflate(inflater, container, false)
        viewBinding.keywordEditor.requestFocus()
        viewBinding.searchButton.onFocusChangeListener =
            View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.alpha = 0.5f
                } else {
                    view.alpha = 1f
                }
            }
        viewBinding.searchButton.setOnClickListener {
            submitText()
        }
        return viewBinding.root
    }

}