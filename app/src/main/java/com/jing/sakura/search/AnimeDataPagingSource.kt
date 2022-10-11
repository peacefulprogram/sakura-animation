package com.jing.sakura.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.jing.sakura.data.AnimeData
import com.jing.sakura.repo.WebPageRepository

class AnimeDataPagingSource(
    private val keyword: String,
    private val webPageRepository: WebPageRepository
) : PagingSource<Int, AnimeData>() {
    override fun getRefreshKey(state: PagingState<Int, AnimeData>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeData> {
        val page = params.key ?: 1
        val prevKey = if (page > 1) page - 1 else null
        return try {
            val data = webPageRepository.searchAnimation(keyword, page)
            val nextKey = if (data.hasNextPage) page + 1 else null
            LoadResult.Page(data.animeList, prevKey, nextKey)
        } catch (ex: Exception) {
            LoadResult.Error(ex)
        }

    }

}