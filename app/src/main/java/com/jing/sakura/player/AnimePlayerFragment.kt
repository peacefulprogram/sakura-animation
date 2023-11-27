package com.jing.sakura.player

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toDrawable
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import com.jing.sakura.SakuraApplication
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.dpToPixels
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.extend.showLongToast
import com.jing.sakura.extend.showShortToast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.qualifier

class AnimePlayerFragment : VideoSupportFragment() {

    private lateinit var viewModel: VideoPlayerViewModel
    private var exoplayer: ExoPlayer? = null

    private var glue: ProgressTransportControlGlue<LeanbackPlayerAdapter>? = null

    private lateinit var mProgressBarManager: ProgressBarManager

    private val okHttpClient: OkHttpClient =
        get(qualifier = qualifier(SakuraApplication.KoinOkHttpClient.MEDIA))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentArg = requireActivity().intent.getSerializableExtra(
            "video",
        ) as NavigateToPlayerArg
        viewModel = get { parametersOf(intentArg) }
        viewModel.init(

        )
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Create the MediaSession that will be used throughout the lifecycle of this Fragment.
    }

    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.background = Color.BLACK.toDrawable()
        mProgressBarManager = ProgressBarManager()
        mProgressBarManager.setRootView(view as ViewGroup)
        mProgressBarManager.enableProgressBar()
        progressBarManager.initialDelay = 0L
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

                viewModel.videoUrl.collectLatest { urlAndTime ->
                    when (urlAndTime) {
                        is Resource.Success -> {
                            mProgressBarManager.hide()

                            val okhttpDataSourceFactory =
                                OkHttpDataSource.Factory { req -> okHttpClient.newCall(req) }
                                    .apply {
                                        setDefaultRequestProperties(urlAndTime.data.headers)
                                    }
                            val isM3u8 = urlAndTime.data.videoUrl.contains("m3u8")
                            val mediaSource =
                                DefaultMediaSourceFactory(okhttpDataSourceFactory).createMediaSource(
                                    MediaItem.Builder().setUri(urlAndTime.data.videoUrl)
                                        .apply {
                                            if (isM3u8) {
                                                setMimeType(MimeTypes.APPLICATION_M3U8)
                                            }
                                        }
                                        .build()
                                )
                            exoplayer?.setMediaSource(mediaSource)
                            if (urlAndTime.data.lastPlayPosition > 0) {
                                // 距离结束小于10秒,当作播放结束
                                if (urlAndTime.data.videoDuration > 0 && urlAndTime.data.videoDuration - urlAndTime.data.lastPlayPosition < 10_000) {
                                    requireContext().showShortToast("上次已播放完,将从头开始播放")
                                } else {
                                    val seekTo = urlAndTime.data.lastPlayPosition
                                    exoplayer?.seekTo(seekTo)
                                    requireContext().showShortToast("已定位到上次播放位置:${(seekTo / 1000).secondsToMinuteAndSecondText()}")
                                }
                            }
                            exoplayer?.prepare()
                            exoplayer?.play()
                            viewModel.changePlayingEpisode(urlAndTime.data.episode)
                        }

                        is Resource.Loading -> {
                            mProgressBarManager.show()
                        }

                        is Resource.Error -> {
                            requireContext().showLongToast("加载视频链接错误: ${urlAndTime.message}")
                            mProgressBarManager.hide()
                        }
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

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        viewModel.startSaveHistory()
                    } else {
                        viewModel.stopSaveHistory()
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

    @OptIn(UnstableApi::class)
    private fun prepareGlue(localExoplayer: ExoPlayer) {
        ProgressTransportControlGlue(
            context = requireContext(),
            lifeCycleScope = lifecycleScope,
            activity = requireActivity(),
            impl = LeanbackPlayerAdapter(
                requireContext(),
                localExoplayer,
                PLAYER_UPDATE_INTERVAL_MILLIS.toInt()
            ),
            updateProgress = {
                viewModel.onPlayPositionChange(
                    localExoplayer.currentPosition,
                    localExoplayer.contentDuration
                )
            },
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


    companion object {
        // Update the player UI fairly often. The frequency of updates affects several UI components
        // such as the smoothness of the progress bar and time stamp labels updating. This value can
        // be tweaked for better performance.
        private const val PLAYER_UPDATE_INTERVAL_MILLIS = 100L

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