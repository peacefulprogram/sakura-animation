package com.jing.sakura.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WebPageRepository
) : ViewModel() {

    private val _homePageData = MutableStateFlow<Resource<HomePageData>>(Resource.Loading)

    val homePageData: StateFlow<Resource<HomePageData>>
        get() = _homePageData

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _homePageData.emit(Resource.Loading)
            try {
                repository.fetchHomePage().also {
                    _homePageData.emit(Resource.Success(it))
                }
            } catch (ex: Exception) {
                Log.e("homepage", "请求数据失败", ex)

                val message = "请求数据失败:" + ex.message
                _homePageData.emit(Resource.Error(message))
            }
        }
    }

}