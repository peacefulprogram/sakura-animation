package com.jing.sakura.player

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.viewModels
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.dpToPixels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration

@AndroidEntryPoint
class AnimePlayerFragment : VideoSupportFragment() {

    private val viewModel by viewModels<VideoPlayerViewModel>()
    private var exoplayer: ExoPlayer? = null

    private var glue: ProgressTransportControlGlue<LeanbackPlayerAdapter>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnimePlayerFragmentArgs.fromBundle(requireArguments()).let {
            viewModel.init(
                it.animeDetail.animeName,
                it.animeDetail.playIndex,
                it.animeDetail.playlist
            )
        }
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Create the MediaSession that will be used throughout the lifecycle of this Fragment.
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerTitle.collectLatest {
                    glue?.title = it
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.videoUrl.collectLatest {
                    when (it) {
                        is Resource.Success -> {
                            MediaItem.fromUri(it.data).let {
                                exoplayer?.setMediaItem(it)
                                exoplayer?.prepare()
                                exoplayer?.play()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        exoplayer = buildPlayer()
    }

    override fun onStop() {
        super.onStop()
        destroyPlayer()
    }


    private fun buildPlayer() =
        ExoPlayer.Builder(requireContext()).build().apply {
            prepareGlue(this)
            playWhenReady = true
            addListener(object : Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == ExoPlayer.STATE_ENDED) {
                        viewModel.playNextEpisodeIfExists()
                    }
                }
            })
        }

    private fun destroyPlayer() {
        exoplayer?.let {
            // Pause the player to notify listeners before it is released.
            it.pause()
            it.release()
            exoplayer = null
        }
    }

    private fun prepareGlue(localExoplayer: ExoPlayer) {
        ProgressTransportControlGlue(
            context = requireContext(),
            lifeCycleScope = lifecycleScope,
            navController = findNavController(),
            impl = LeanbackPlayerAdapter(
                requireContext(),
                localExoplayer,
                PLAYER_UPDATE_INTERVAL_MILLIS.toInt()
            ),
            updateProgress = onProgressUpdate,
            chooseEpisode = this::openPlayListDialogAndChoose
        ).apply {
            glue = this
            host = VideoSupportFragmentGlueHost(this@AnimePlayerFragment)
            isControlsOverlayAutoHideEnabled = true
            title = viewModel.playerTitle.value
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


    private fun openPlayListDialogAndChoose() {
        val fragmentManager = requireActivity().supportFragmentManager
        ChooseEpisodeDialog(
            dataList = viewModel.playList,
            defaultSelectIndex = viewModel.playIndex,
            viewWidth = 60.dpToPixels(requireContext()).toInt(),
            getText = { _, item -> item.episode }
        ) { index, _ ->
            viewModel.playEpisodeOfIndex(index)
        }.apply {
            showNow(fragmentManager, "")
        }
    }

}