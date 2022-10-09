package com.jing.sakura.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WebPageRepository
) : ViewModel() {

    private val _homePageData = MutableLiveData<Resource<HomePageData>>(Resource.Empty())

    val homePageData: LiveData<Resource<HomePageData>>
        get() = _homePageData


    init {
        loadData()
    }


    fun loadData() {
        if (homePageData.value is Resource.Loading) {
            return
        }
        _homePageData.postValue(Resource.Loading())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.fetchHomePage().also {
                    _homePageData.postValue(Resource.Success(it))
                }
            } catch (ex: Exception) {
                Log.e("homepage", "请求数据失败", ex)

                val message = "请求数据失败:" + ex.message
                _homePageData.postValue(Resource.Error(message))
            }
        }
    }

}