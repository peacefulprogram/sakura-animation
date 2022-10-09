package com.jing.sakura.extend

import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun <T> Fragment.observeLiveData(data: LiveData<T>, observer: Observer<T>) {
    data.observe(viewLifecycleOwner, observer)
}