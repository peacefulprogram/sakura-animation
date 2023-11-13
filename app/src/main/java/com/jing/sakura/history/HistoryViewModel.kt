package com.jing.sakura.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val videoHistoryDao: VideoHistoryDao,
    private val repository: WebPageRepository
) :
    ViewModel() {

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = HistoryRemoteMediator()
    ) {
        videoHistoryDao.queryHistory()
    }.flow


    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            videoHistoryDao.deleteAll()
        }
    }

    fun getSourceName(sourceId: String): String = repository.requireAnimationSource(sourceId).name

    fun deleteHistoryByAnimeId(animeId: String, sourceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            videoHistoryDao.deleteHistoryByAnimeId(animeId, sourceId)
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    private class HistoryRemoteMediator() :
        RemoteMediator<Int, VideoHistoryEntity>() {

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, VideoHistoryEntity>
        ): MediatorResult {
            return MediatorResult.Success(true)
        }
    }
}