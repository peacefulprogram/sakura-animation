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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetailPageViewModel constructor(
    private val url: String,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao
) : ViewModel() {

    val animeId = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf('.'))

    private var _detailPageData = MutableStateFlow<Resource<AnimeDetailPageData>>(Resource.Loading)

    val detailPageData: StateFlow<Resource<AnimeDetailPageData>>
        get() = _detailPageData

    private var _latestProgress = MutableStateFlow<Resource<VideoHistoryEntity>>(Resource.Loading)

    val latestProgress: StateFlow<Resource<VideoHistoryEntity>>
        get() = _latestProgress

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _detailPageData.emit(Resource.Loading)
            try {
                repository.fetchDetailPage(url).also {
                    _detailPageData.emit(Resource.Success(it))
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
            videoHistoryDao.queryLastHistoryOfAnimeId(animeId)?.let {
                _latestProgress.emit(Resource.Success(it))
            }
        }
    }

}