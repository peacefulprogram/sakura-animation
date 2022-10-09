package com.jing.sakura.player

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import java.time.Duration

class AnimePlayerFragment : VideoSupportFragment() {
    private var exoplayer: ExoPlayer? = null
    private lateinit var animeTitle: String
    private lateinit var videoUrl: String
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnimePlayerFragmentArgs.fromBundle(requireArguments()).let {
            videoUrl = it.videoUrl
            animeTitle = it.title
        }
        // Create the MediaSession that will be used throughout the lifecycle of this Fragment.
        createMediaSession()
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(requireContext(), MEDIA_SESSION_TAG)

        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            setQueueNavigator(SingleVideoQueueNavigator(animeTitle, mediaSession))
        }

    }

    override fun onStart() {
        super.onStart()
        exoplayer = buildPlayer(videoUrl, requireContext())
    }

    override fun onStop() {
        super.onStop()
        destroyPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Releasing the mediaSession due to inactive playback and setting token for cast to null.
        mediaSession.release()
    }


    private fun buildPlayer(videoUrl: String, context: Context) =
        ExoPlayer.Builder(context).build().apply {
            prepareGlue(this)

            val mediaItem = MediaItem.Builder()
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setUri(videoUrl)
                .build()
            addListener(object : Player.Listener {

            })
            val source =
                HlsMediaSource.Factory(DefaultHlsDataSourceFactory(DefaultHttpDataSource.Factory()))
                    .createMediaSource(mediaItem)
            addMediaSource(source)
            prepare()
        }

    private fun destroyPlayer() {
        mediaSession.isActive = false
        mediaSessionConnector.setPlayer(null)
        exoplayer?.let {
            // Pause the player to notify listeners before it is released.
            it.pause()
            it.release()
            exoplayer = null
        }
    }

    private fun prepareGlue(localExoplayer: ExoPlayer) {
        ProgressTransportControlGlue(
            requireContext(),
            LeanbackPlayerAdapter(
                requireContext(),
                localExoplayer,
                PLAYER_UPDATE_INTERVAL_MILLIS.toInt()
            ),
            onProgressUpdate
        ).apply {
            host = VideoSupportFragmentGlueHost(this@AnimePlayerFragment)
            title = animeTitle
            // Enable seek manually since PlaybackTransportControlGlue.getSeekProvider() is null,
            // so that PlayerAdapter.seekTo(long) will be called during user seeking.
            isSeekEnabled = true
        }
    }


    private val onProgressUpdate: () -> Unit = {
        // TODO(benbaxter): Calculate when end credits are displaying and show the next episode for
        //  episodic content.
    }


    companion object {
        // Update the player UI fairly often. The frequency of updates affects several UI components
        // such as the smoothness of the progress bar and time stamp labels updating. This value can
        // be tweaked for better performance.
        private val PLAYER_UPDATE_INTERVAL_MILLIS = Duration.ofMillis(100).toMillis()

        // A short name to identify the media session when debugging.
        private const val MEDIA_SESSION_TAG = "ReferenceAppKotlin"
    }

}