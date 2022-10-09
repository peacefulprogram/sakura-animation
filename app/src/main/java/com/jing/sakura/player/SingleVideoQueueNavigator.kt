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

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.VisibleForTesting
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator

/**
 * A QueueNavigator that handles a single video.
 *
 * This QueueNavigator provides the MediaDescriptionCompat for the passed Video. Since it handles
 * a single video only, it can build the description once it is first requested and then reuse
 * that description for all future requests.
 */
class SingleVideoQueueNavigator(animeTitle:String, mediaSession: MediaSessionCompat) :
    TimelineQueueNavigator(mediaSession) {
    private val mediaDescriptionCompat by lazy { getMediaDescription(animeTitle) }

    override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
        return mediaDescriptionCompat
    }

    @VisibleForTesting
    fun getMediaDescription(animeTitle: String): MediaDescriptionCompat {
        return MediaDescriptionCompat.Builder()
            .setTitle(animeTitle)
            .build()
    }
}
