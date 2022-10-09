package com.jing.sakura.splash

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashViewModel : ViewModel() {
    private val _shouldGoHome = MutableLiveData(false)

    val shouldGoHome: LiveData<Boolean>
        get() = _shouldGoHome

    init {
        viewModelScope.launch(Dispatchers.IO) {
            delay(1500)
            _shouldGoHome.postValue(true)
        }
    }
}