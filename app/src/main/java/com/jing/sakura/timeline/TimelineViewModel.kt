package com.jing.sakura.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val repository: WebPageRepository,
    val sourceId:String
) : ViewModel() {

    private val _timelines: MutableStateFlow<Resource<UpdateTimeLine>> =
        MutableStateFlow(Resource.Loading)
    val timelines: StateFlow<Resource<UpdateTimeLine>>
        get() = _timelines

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _timelines.emit(Resource.Loading)
                _timelines.emit(Resource.Success(repository.fetchUpdateTimeline(sourceId)))
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                _timelines.emit(Resource.Error("加载失败:${ex.message}"))
            }
        }
    }
}