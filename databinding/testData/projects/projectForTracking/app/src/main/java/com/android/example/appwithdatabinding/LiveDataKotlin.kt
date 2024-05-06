package com.android.example.appwithdatabinding

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class NameViewModel : ViewModel() {
  // Create a LiveData with a String
  val currentName: MutableLiveData<String> by lazy { MutableLiveData<String>() }
}
