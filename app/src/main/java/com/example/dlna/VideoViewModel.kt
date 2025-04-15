package com.example.dlna

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class VideoViewModel : ViewModel() {
    val state = MutableLiveData<Boolean>()
}