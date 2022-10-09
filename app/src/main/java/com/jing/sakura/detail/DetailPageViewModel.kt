package com.jing.sakura.detail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailPageViewModel @Inject constructor(
    private val repository: WebPageRepository
) : ViewModel() {

    private var _detailPageData = MutableLiveData<Resource<AnimeDetailPageData>>(Resource.Empty())

    val detailPageData: LiveData<Resource<AnimeDetailPageData>>
        get() = _detailPageData


    fun loadData(url: String) {
        if (detailPageData.value is Resource.Loading) {
            return
        }
        _detailPageData.postValue(Resource.Loading())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.fetchDetailPage(url).also {
                    _detailPageData.postValue(Resource.Success(it))
                }
            } catch (ex: Exception) {
                Log.e("homepage", "请求数据失败", ex)

                val message = "请求数据失败:" + ex.message
                _detailPageData.postValue(Resource.Error(message))
            }
        }
    }

}