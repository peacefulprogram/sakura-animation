package com.jing.sakura.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jing.sakura.repo.WebPageRepository

class SearchResultViewModel(
    private val keyword: String,
    private val webPageRepository: WebPageRepository,
    val sourceId:String
) : ViewModel() {

    val pager = Pager(
        PagingConfig(pageSize = webPageRepository.requireAnimationSource(sourceId).pageSize),
        pagingSourceFactory = {
            AnimeDataPagingSource {
                webPageRepository.searchAnimation(keyword, it, sourceId)
            }
        }
    ).flow.cachedIn(viewModelScope)

}