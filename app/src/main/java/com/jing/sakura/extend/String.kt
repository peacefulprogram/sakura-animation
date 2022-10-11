package com.jing.sakura.extend

import java.net.URLEncoder
import java.nio.charset.Charset

fun String.encodeUrl(charset: Charset = Charsets.UTF_8): String =
    URLEncoder.encode(this, charset.name())