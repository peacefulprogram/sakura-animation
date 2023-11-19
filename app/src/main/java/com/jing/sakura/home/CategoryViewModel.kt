package com.jing.sakura.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.VideoCategoryGroup
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.search.AnimeDataPagingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val webPageRepository: WebPageRepository,
    val sourceId: String
) : ViewModel() {

    private val _categories = MutableStateFlow<Resource<List<VideoCategoryGroup>>>(Resource.Loading)
    val categories: StateFlow<Resource<List<VideoCategoryGroup>>>
        get() = _categories

    private val _selectedCategories = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedCategories: StateFlow<Map<String, String>>
        get() = _selectedCategories

    private val _userSelectedCategories = MutableStateFlow<MutableMap<String, String>>(
        mutableMapOf()
    )
    val userSelectedCategories: StateFlow<MutableMap<String, String>>
        get() = _userSelectedCategories

    @Volatile
    private var queryCategories = emptyList<NamedValue<String>>()

    init {
        loadCategories()
    }

    val pager = Pager(
        config = PagingConfig(pageSize = webPageRepository.requireAnimationSource(sourceId).pageSize)
    ) {
        AnimeDataPagingSource {
            if (queryCategories.isEmpty()) {
                AnimePageData(it, hasNextPage = false, animeList = emptyList())
            } else {
                webPageRepository.queryByCategory(queryCategories, it, sourceId = sourceId)
            }
        }
    }
        .flow
        .cachedIn(viewModelScope)


    fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            _categories.emit(Resource.Loading)
            try {
                val categoryGroups = webPageRepository.getVideoCategories(sourceId)
                val defaultValues = mutableMapOf<String, String>()
                categoryGroups.forEach { group ->
                    defaultValues[group.key] = group.defaultValue
                }
                queryCategories =
                    defaultValues.entries.map { NamedValue(name = it.key, value = it.value) }
                _selectedCategories.emit(defaultValues)
                _userSelectedCategories.emit(HashMap(defaultValues))
                _categories.emit(Resource.Success(categoryGroups))
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                Log.e(TAG, "loadCategories: ${ex.message}", ex)
                _categories.emit(Resource.Error("查询可选分类错误: ${ex.message}"))
            }
        }
    }

    fun applyUserSelectedCategories(): ShouldRefresh {
        val m1 = _selectedCategories.value
        val m2 = _userSelectedCategories.value
        val notSame = m1.entries.any { it.value != m2[it.key] }
        if (notSame) {
            this.queryCategories = m2.entries.map { NamedValue(name = it.key, value = it.value) }
            _selectedCategories.update { HashMap(m2) }
            return true
        }
        return false
    }

    fun onUserSelect(key: String, value: String) {
        _userSelectedCategories.update {
            HashMap(it).apply { this[key] = value }
        }
    }

    companion object {
        private const val TAG = "CategoryViewModel"
    }

}

typealias ShouldRefresh = Boolean