package com.jing.sakura.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.jing.sakura.R

class PlaybackActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
    }

    companion object {
        fun startActivity(context: Context, arg: NavigateToPlayerArg) {
            Intent(context, PlaybackActivity::class.java).apply {
                putExtra("video", arg)
                context.startActivity(this)
            }
        }
    }
}