package com.android.example.appwithdatabinding

import androidx.databinding.Observable
import androidx.databinding.ObservableField

open class KtObservable : Observable {
  override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {}

  override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {}
}

open class KtInheritedObservable : KtObservable()

open class KtInheritsFromJava : InheritedObservable() {
  val observableField = ObservableField("BLah")
}
