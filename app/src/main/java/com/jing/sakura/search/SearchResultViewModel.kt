package com.jing.sakura.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jing.sakura.data.AnimeData
import com.jing.sakura.repo.WebPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SearchResultViewModel @Inject constructor(
    private val webPageRepository: WebPageRepository
) : ViewModel() {

    private fun getPagingDataFlow(keyword: String): Flow<PagingData<AnimeData>> {
        return Pager(
            PagingConfig(24),
            pagingSourceFactory = {
                AnimeDataPagingSource(
                    keyword,
                    webPageRepository
                )
            }
        ).flow
    }

    fun getPagingData(keyword: String): Flow<PagingData<AnimeData>> {
        return getPagingDataFlow(keyword).cachedIn(viewModelScope)
    }
}