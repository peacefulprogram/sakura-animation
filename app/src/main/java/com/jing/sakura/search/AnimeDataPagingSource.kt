package com.jing.sakura.search

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimePageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimeDataPagingSource(
    private val dataProvider: suspend (page: Int) -> AnimePageData
) : PagingSource<Int, AnimeData>() {
    override fun getRefreshKey(state: PagingState<Int, AnimeData>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeData> {
        val page = params.key ?: 1
        val prevKey = if (page > 1) page - 1 else null
        return try {
            val data = withContext(Dispatchers.IO){
                dataProvider.invoke(page)
            }
            val nextKey = if (data.hasNextPage) page + 1 else null
            LoadResult.Page(data.animeList, prevKey, nextKey)
        } catch (ex: Exception) {
            if (ex is CancellationException) {
                throw ex
            }
            Log.e(TAG, "load: ${ex.message}", ex)
            LoadResult.Error(ex)
        }

    }

    companion object {
        private const val TAG = "AnimeDataPagingSource"
    }
}