package com.jing.sakura.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jing.sakura.data.AnimeData
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

class SearchResultViewModel :
    ViewModel() {

    private fun getPagingDataFlow(keyword: String): Flow<PagingData<AnimeData>> {
        return Pager(
            PagingConfig(24),
            pagingSourceFactory = {
                AnimeDataPagingSource(
                    keyword,
                    WebPageRepository(OkHttpClient())
                )
            }
        ).flow
    }

    fun getPagingData(keyword: String): Flow<PagingData<AnimeData>> {
        return getPagingDataFlow(keyword).cachedIn(viewModelScope)
    }
}