package com.jing.sakura.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.regex.Pattern
import javax.inject.Inject


@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao
) : ViewModel() {

    private val TAG = VideoPlayerViewModel::class.java.simpleName

    private var _animeName = ""

    private lateinit var anime: NavigateToPlayerArg

    private var _playList = emptyList<AnimePlayListEpisode>()

    private var _saveHistoryJob: Job? = null

    private var currentPlayPosition: Long = 0L

    private var videoDuration: Long = 0L

    val playList: List<AnimePlayListEpisode>
        get() = _playList

    private val _playIndex = MutableStateFlow(-1)
    val playIndex: Int
        get() = _playIndex.value

    private val _playerTitle = MutableStateFlow("")

    val playerTitle: MutableStateFlow<String>
        get() = _playerTitle

    private val _videoUrl = MutableStateFlow<Resource<EpisodeUrlAndHistory>>(Resource.Loading())
    val videoUrl: StateFlow<Resource<EpisodeUrlAndHistory>>
        get() = _videoUrl


    init {
        viewModelScope.launch {
            _playIndex.collectLatest { index ->
                if (index >= 0) {
                    val episode = _playList[index]
                    _playerTitle.emit("$_animeName - ${episode.episode}")
                    fetchVideoUrl(episode)
                }
            }
        }
    }

    fun init(anime: NavigateToPlayerArg) {
        this.anime = anime
        viewModelScope.launch {
            _animeName = anime.animeName
            this@VideoPlayerViewModel._playList = anime.playlist
            _playIndex.emit(anime.playIndex)
        }
    }

    private suspend fun fetchVideoUrl(episode: AnimePlayListEpisode) {
        _videoUrl.emit(Resource.Loading())
        withContext(Dispatchers.IO) {
            try {
                val resp = repository.fetchVideoUrl(episode.url)
                val history = videoHistoryDao.queryHistoryByEpisodeId(
                    episodeId = episode.episodeId
                )
                when (resp) {
                    is Resource.Error -> _videoUrl.emit(Resource.Error(resp.message))
                    is Resource.Success -> _videoUrl.emit(
                        Resource.Success(
                            EpisodeUrlAndHistory(
                                videoUrl = resp.data,
                                videoDuration = history?.videoDuration ?: 0L,
                                lastPlayPosition = history?.lastPlayTime ?: 0L
                            )
                        )
                    )
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchVideoUrl: ${e.message}", e)
                _videoUrl.emit(Resource.Error(e.message ?: ""))
            }
        }
    }

    fun playEpisodeOfIndex(index: Int) {
        viewModelScope.launch {
            _playIndex.emit(index)
        }
    }

    fun startSaveHistory() {
        stopSaveHistory()
        _saveHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val history = withContext(Dispatchers.Main) {
                    VideoHistoryEntity(
                        animeId = anime.animeId,
                        animeName = anime.animeName,
                        episodeId = playList[playIndex].episodeId,
                        lastEpisodeName = playList[playIndex].episode,
                        updateTime = System.currentTimeMillis(),
                        lastPlayTime = currentPlayPosition,
                        coverUrl = anime.coverUrl,
                        videoDuration = videoDuration
                    )
                }
                videoHistoryDao.saveHistory(history)
                delay(5000L)
            }
        }
    }

    fun stopSaveHistory() {
        _saveHistoryJob?.cancel()
        _saveHistoryJob = null
    }

    fun playNextEpisodeIfExists() {
        val nextIndex = findNextEpisodeIndex() ?: return
        playEpisodeOfIndex(nextIndex)
    }

    private fun findNextEpisodeIndex(): Int? {
        if (_playList.size < 2) {
            return null
        }
        val idx = _playIndex.value
        if (idx < 0) {
            return null
        }
        val episode = _playList[idx]
        val num = extractNumberFromText(episode.episode)
        if (num != null) {
            if (idx + 1 < _playList.size) {
                val nextNum = extractNumberFromText(_playList[idx + 1].episode)
                if (nextNum != null && nextNum > num) {
                    return idx + 1
                }
            }
            if (idx > 0) {
                val prevNum = extractNumberFromText(_playList[idx - 1].episode)
                if (prevNum != null && prevNum > num) {
                    return idx - 1
                }
            }
        }
        return null
    }

    private fun extractNumberFromText(text: String): Int? {
        return Pattern.compile("\\d+").matcher(text)
            .takeIf { it.find() }
            ?.run {
                group().toInt()
            }
    }

    fun onPlayPositionChange(currentPosition: Long, duration: Long) {
        this.currentPlayPosition = currentPosition
        this.videoDuration = duration
    }
}