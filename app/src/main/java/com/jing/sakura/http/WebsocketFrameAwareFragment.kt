package com.jing.sakura.http

import androidx.fragment.app.Fragment

abstract class WebsocketFrameAwareFragment : Fragment() {

    abstract fun onMessage(operation: WebsocketOperation, content: String): WebsocketResult
}