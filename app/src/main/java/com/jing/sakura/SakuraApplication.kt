package com.jing.sakura

import android.app.Application
import com.jing.sakura.http.WebServerContext
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SakuraApplication : Application() {
    init {
        WebServerContext.androidContext = this
    }
}