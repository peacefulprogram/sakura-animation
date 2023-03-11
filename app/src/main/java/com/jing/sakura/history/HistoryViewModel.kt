package com.jing.sakura.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val videoHistoryDao: VideoHistoryDao) :
    ViewModel() {

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = HistoryRemoteMediator()
    ) {
        videoHistoryDao.queryHistory()
    }.flow


    fun deleteAllHistory(onFinish: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            videoHistoryDao.deleteAll()
            withContext(Dispatchers.Main) {
                onFinish()
            }
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