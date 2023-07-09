package com.jing.sakura.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jing.sakura.repo.WebPageRepository

class SearchResultViewModel(
    private val keyword: String,
    private val webPageRepository: WebPageRepository
) : ViewModel() {

    val pager = Pager(
        PagingConfig(20),
        pagingSourceFactory = {
            AnimeDataPagingSource(
                keyword,
                webPageRepository,
            )
        }
    ).flow.cachedIn(viewModelScope)

}