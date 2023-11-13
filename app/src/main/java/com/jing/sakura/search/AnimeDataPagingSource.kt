package com.jing.sakura.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.jing.sakura.data.AnimeData
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimeDataPagingSource(
    private val keyword: String,
    private val webPageRepository: WebPageRepository,
    val sourceId:String
) : PagingSource<Int, AnimeData>() {
    override fun getRefreshKey(state: PagingState<Int, AnimeData>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeData> {
        val page = params.key ?: 1
        val prevKey = if (page > 1) page - 1 else null
        return try {
            val data = withContext(Dispatchers.IO){
                webPageRepository.searchAnimation(keyword, page, sourceId = sourceId)
            }
            val nextKey = if (data.hasNextPage) page + 1 else null
            LoadResult.Page(data.animeList, prevKey, nextKey)
        } catch (ex: Exception) {
            if (ex is CancellationException) {
                throw ex
            }
            LoadResult.Error(ex)
        }

    }

}