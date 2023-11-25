/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jing.sakura.player

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import com.jing.sakura.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Custom [PlaybackTransportControlGlue] that exposes a callback when the progress is updated.
 *
 * The callback is triggered based on a progress interval defined in several ways depending on the
 * [PlayerAdapter].
 *
 * [LeanbackPlayerAdapter] example:
 * ```
 *     private val updateMillis = 16
 *     LeanbackPlayerAdapter(context, exoplayer, updateMillis)
 * ```
 *
 * [MediaPlayerAdapter] example:
 * ```
 *     object : MediaPlayerAdapter(context) {
 *         private val updateMillis = 16
 *         override fun getProgressUpdatingInterval(): Int {
 *             return updateMillis
 *         }
 *     }
 * ```
 */
class ProgressTransportControlGlue<T : PlayerAdapter>(
    context: Context,
    private val lifeCycleScope: CoroutineScope,
    private val activity: Activity,
    impl: T,
    private val updateProgress: () -> Unit,
    private val chooseEpisode: () -> Unit = {}
) : PlaybackTransportControlGlue<T>(context, impl) {

    private var backPressed = false

    private val episodeListAction: Action = object : Action(10) {
        init {
            icon = ContextCompat.getDrawable(context, R.drawable.play_list)
        }
    }


    private val replayAction: Action = object : Action(20) {
        init {
            icon = ContextCompat.getDrawable(context, R.drawable.replay)
        }
    }

    override fun onCreatePrimaryActions(primaryActionsAdapter: ArrayObjectAdapter) {
        // super.onCreatePrimaryActions() will create the play / pause action.
        super.onCreatePrimaryActions(primaryActionsAdapter)

        // Add the rewind and fast forward actions following the play / pause action.
        primaryActionsAdapter.apply {
            add(episodeListAction)
            add(replayAction)
        }
    }

    override fun onUpdateProgress() {
        super.onUpdateProgress()
        updateProgress()
    }

    override fun onActionClicked(action: Action) {
        // Primary actions are handled manually. The superclass handles default play/pause action.
        when (action) {
            replayAction -> playerAdapter.seekTo(0L)
            episodeListAction -> chooseEpisode()
            else -> super.onActionClicked(action)
        }
    }

    override fun onKey(v: View?, keyCode: Int, keyEvent: KeyEvent): Boolean {

        if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                return true
            }
            if (host.isControlsOverlayVisible) {
                host.hideControlsOverlay(false)
                return true
            }
            if (!playerAdapter.isPlaying) {
                backPressed = false
                activity.finish()
                return true
            }
            if (backPressed) {
                activity.finish()
                return true
            }
            backPressed = true
            Toast.makeText(context, "再按一次退出播放", Toast.LENGTH_SHORT).show()
            lifeCycleScope.launch {
                delay(2000)
                backPressed = false
            }
            return true
        }
        if (keyEvent.keyCode == KeyEvent.KEYCODE_MENU && keyEvent.action == KeyEvent.ACTION_UP) {
            chooseEpisode()
            return true
        }
        if (keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && !host.isControlsOverlayVisible) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                return true
            }
            if (playerAdapter.isPlaying) {
                playerAdapter.pause()
            } else {
                playerAdapter.play()
            }
            return true
        }
        return super.onKey(v, keyCode, keyEvent)
    }

    companion object {
        private val THIRTY_SECONDS = TimeUnit.SECONDS.toMillis(30)
    }
}
