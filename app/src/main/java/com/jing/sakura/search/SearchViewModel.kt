package com.jing.sakura.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.jing.sakura.room.SearchHistoryDao
import com.jing.sakura.room.SearchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    val searchHistoryPager = Pager(config = PagingConfig(pageSize = 10)) {
        searchHistoryDao.queryHistory()
    }.flow

    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.Default) {
            searchHistoryDao.deleteAllHistory()
        }
    }

    fun deleteHistory(keyword: String) {
        viewModelScope.launch(Dispatchers.Default) {
            searchHistoryDao.deleteHistory(keyword)
        }
    }

    fun saveHistory(keyword: String) {
        viewModelScope.launch(Dispatchers.Default) {
            searchHistoryDao.saveHistory(
                SearchHistoryEntity(
                    keyword = keyword,
                    searchTime = System.currentTimeMillis()
                )
            )
        }
    }
}