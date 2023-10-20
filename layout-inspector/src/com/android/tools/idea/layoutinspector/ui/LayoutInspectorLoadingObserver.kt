/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.util.ListenerCollection
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class observes [LayoutInspector] and keeps track of when it is in a loading state. It can be
 * used for example to show loading indicators in the UI.
 */
class LayoutInspectorLoadingObserver(
  parentDisposable: Disposable,
  private val layoutInspector: LayoutInspector
) : Disposable {
  interface Listener {
    fun onStartLoading()

    fun onStopLoading()
  }

  var listeners = ListenerCollection.createWithDirectExecutor<Listener>()

  val isLoading
    get() = _isLoading.get()

  private val _isLoading = AtomicBoolean(false)

  private val stopInspectorListener: () -> Unit = { setIsLoading(false) }

  private val selectedProcessListener: () -> Unit = {
    if (layoutInspector.processModel?.selectedProcess?.isRunning == true) {
      setIsLoading(true)
    }
    if (layoutInspector.processModel?.selectedProcess == null) {
      setIsLoading(false)
    }
  }

  private val inspectorModelModificationListener =
    InspectorModel.ModificationListener { _, _, _ -> setIsLoading(false) }

  init {
    Disposer.register(parentDisposable, this)
    layoutInspector.stopInspectorListeners.add(stopInspectorListener)
    layoutInspector.processModel?.addSelectedProcessListeners(
      Executors.newSingleThreadExecutor(),
      selectedProcessListener
    )
    layoutInspector.inspectorModel.addModificationListener(inspectorModelModificationListener)
  }

  override fun dispose() {
    listeners.clear()

    layoutInspector.stopInspectorListeners.remove(stopInspectorListener)
    layoutInspector.processModel?.removeSelectedProcessListener(selectedProcessListener)
    layoutInspector.inspectorModel.removeModificationListener(inspectorModelModificationListener)
  }

  private fun setIsLoading(isLoading: Boolean) {
    _isLoading.set(isLoading)

    if (isLoading) {
      listeners.forEach { it.onStartLoading() }
    } else {
      listeners.forEach { it.onStopLoading() }
    }
  }
}
