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

    @Volatile
    private var rawCategoryGroups = emptyList<VideoCategoryGroup>()

    private val _categories =
        MutableStateFlow<Resource<List<CategoryGroupWrapper>>>(Resource.Loading)
    val categories: StateFlow<Resource<List<CategoryGroupWrapper>>>
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

    private val existsVideoIds = mutableSetOf<String>()

    @Volatile
    private var categoryRowNextId = 0

    init {
        loadCategories()
    }

    val pager = Pager(
        config = PagingConfig(
            pageSize = webPageRepository.requireAnimationSource(sourceId).pageSize
        )
    ) {
        AnimeDataPagingSource { page ->
            if (page == 1) {
                // 请求第一页时清空数据
                existsVideoIds.clear()
            }
            if (queryCategories.isEmpty()) {
                AnimePageData(page, hasNextPage = false, animeList = emptyList())
            } else {
                val data =
                    webPageRepository.queryByCategory(queryCategories, page, sourceId = sourceId)
                // 去重
                data.copy(animeList = data.animeList.filter { existsVideoIds.add(it.id) })
            }
        }
    }
        .flow
        .cachedIn(viewModelScope)


    fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            _categories.emit(Resource.Loading)
            try {
                val displayCategories = mutableListOf<CategoryGroupWrapper>()
                val categoryGroups = webPageRepository.getVideoCategories(sourceId)
                val defaultValues = mutableMapOf<String, String>()
                categoryGroups.forEach { group ->
                    when (group) {
                        is VideoCategoryGroup.NormalCategoryGroup -> {
                            displayCategories.add(
                                CategoryGroupWrapper(
                                    id = categoryRowNextId++,
                                    group = group
                                )
                            )
                            defaultValues[group.key] = group.defaultValue
                        }

                        is VideoCategoryGroup.DynamicCategoryGroup -> {
                            val actualGroup = group.dependsOnKey.map { k ->
                                NamedValue(name = k, value = defaultValues[k]!!)
                            }.let {
                                group.categoriesProvider(it)
                            }
                            defaultValues[actualGroup.key] = actualGroup.defaultValue
                            displayCategories.add(
                                CategoryGroupWrapper(
                                    id = categoryRowNextId++,
                                    group = actualGroup
                                )
                            )
                        }
                    }
                }
                queryCategories =
                    defaultValues.entries.map { NamedValue(name = it.key, value = it.value) }
                _selectedCategories.emit(defaultValues)
                _userSelectedCategories.emit(HashMap(defaultValues))
                _categories.emit(Resource.Success(displayCategories))
                rawCategoryGroups = categoryGroups
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
        val newValues = mutableMapOf<String, String>()
        val changedGroups = mutableMapOf<Int, CategoryGroupWrapper>()
        val oldValue = _userSelectedCategories.value
        rawCategoryGroups.forEachIndexed { index, group ->
            if (group is VideoCategoryGroup.DynamicCategoryGroup && group.dependsOnKey.contains(key)) {
                val newGroup = group.dependsOnKey.map { k ->
                    NamedValue(name = k, value = if (k == key) value else oldValue[k]!!)
                }.let {
                    group.categoriesProvider(it)
                }
                if (newGroup.categories.isNotEmpty()) {
                    val newValue =
                        oldValue[newGroup.key]?.takeIf { old -> newGroup.categories.any { it.value == old } }
                            ?: newGroup.defaultValue
                    newValues[group.key] = newValue
                }
                changedGroups[index] =
                    CategoryGroupWrapper(id = categoryRowNextId++, group = newGroup)
            } else {
                newValues[group.key] = oldValue[group.key] ?: ""
            }
        }
        newValues[key] = value
        _userSelectedCategories.update {
            newValues
        }
        if (changedGroups.isNotEmpty()) {
            _categories.update {
                when (it) {
                    is Resource.Success -> {
                        val old = it.data
                        val list = List(old.size) { idx ->
                            changedGroups[idx] ?: old[idx]
                        }
                        Resource.Success(list)
                    }

                    else -> it
                }
            }
        }
    }

    companion object {
        private const val TAG = "CategoryViewModel"
    }

}

data class CategoryGroupWrapper(
    val id: Int,
    val group: VideoCategoryGroup.NormalCategoryGroup
)

typealias ShouldRefresh = Boolean