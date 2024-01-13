package com.jing.sakura.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetailPageViewModel constructor(
    private val animeId: String,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao,
    val sourceId: String
) : ViewModel() {

    private var _detailPageData = MutableStateFlow<Resource<AnimeDetailPageData>>(Resource.Loading)

    val detailPageData: StateFlow<Resource<AnimeDetailPageData>>
        get() = _detailPageData

    private var _latestProgress = MutableStateFlow<Resource<VideoHistoryEntity>>(Resource.Loading)

    val latestProgress: StateFlow<Resource<VideoHistoryEntity>>
        get() = _latestProgress

    private var loadDataJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch(Dispatchers.IO) {
            _detailPageData.emit(Resource.Loading)
            try {
                val historyJob = async {
                    videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)
                }
                val data = repository.fetchDetailPage(animeId, sourceId)
                val history = historyJob.await()
                if (history != null) {
                    var position = data.lastPlayEpisodePosition

                    out@ for ((playlistIndex, playList) in data.playLists.withIndex()) {
                        for ((epIndex, ep) in playList.episodeList.withIndex()) {
                            if (ep.episodeId == history.episodeId) {
                                position = Pair(playlistIndex, epIndex)
                                break@out
                            }
                        }
                    }
                    _detailPageData.emit(Resource.Success(data.copy(lastPlayEpisodePosition = position)))
                } else {
                    _detailPageData.emit(Resource.Success(data))
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                Log.e("homepage", "请求数据失败", ex)

                val message = "请求数据失败:" + ex.message
                _detailPageData.emit(Resource.Error(message))
            }
        }
    }

    fun fetchHistory() {
        viewModelScope.launch(Dispatchers.Default) {
            videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)?.let {
                _latestProgress.emit(Resource.Success(it))
            }
        }
    }

}