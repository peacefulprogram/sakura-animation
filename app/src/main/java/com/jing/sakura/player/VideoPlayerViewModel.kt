package com.jing.sakura.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import javax.inject.Inject


@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository: WebPageRepository
) : ViewModel() {

    private val TAG = VideoPlayerViewModel::class.java.simpleName

    private var _animeName = ""

    private var _playList = emptyList<AnimePlayListEpisode>()

    val playList: List<AnimePlayListEpisode>
        get() = _playList

    private val _playIndex = MutableStateFlow(-1)
    val playIndex: Int
        get() = _playIndex.value

    private val _playerTitle = MutableStateFlow("")

    val playerTitle: MutableStateFlow<String>
        get() = _playerTitle

    private val _videoUrl = MutableStateFlow<Resource<String>>(Resource.Loading())
    val videoUrl: StateFlow<Resource<String>>
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

    fun init(animeName: String, playIndex: Int, playlist: List<AnimePlayListEpisode>) {
        viewModelScope.launch {
            _animeName = animeName
            this@VideoPlayerViewModel._playList = playlist
            _playIndex.emit(playIndex)
        }
    }

    private suspend fun fetchVideoUrl(episode: AnimePlayListEpisode) {
        _videoUrl.emit(Resource.Loading())
        withContext(Dispatchers.IO) {
            try {
                val resp = repository.fetchVideoUrl(episode.url)
                _videoUrl.emit(resp)
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
}