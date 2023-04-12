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
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import java.util.concurrent.Executors

/**
 * This class observes [LayoutInspector] and keeps track of when it is in a loading state.
 * It can be used for example to show loading indicators in the UI.
 */
class LayoutInspectorLoadingObserver(private val layoutInspector: LayoutInspector) {
  interface Listener {
    fun onStartLoading()
    fun onStopLoading()
  }

  var listeners = mutableListOf<Listener>()

  var isLoading = false
    private set(value) {
      if (field == value) {
        return
      }

      field = value

      if (isLoading) {
        listeners.forEach { it.onStartLoading() }
      }
      else {
        listeners.forEach { it.onStopLoading() }
      }
    }

  init {
    layoutInspector.stopInspectorListeners.add(this::onStopInspector)
    layoutInspector.processModel?.addSelectedProcessListeners(Executors.newSingleThreadExecutor(), this::onSelectedProcess)
    layoutInspector.inspectorModel.modificationListeners.add(this::onInspectorModelChanged)
  }

  fun destroy() {
    listeners.clear()

    layoutInspector.stopInspectorListeners.remove(this::onStopInspector)
    layoutInspector.inspectorModel.modificationListeners.remove(this::onInspectorModelChanged)
  }

  private fun onStopInspector() {
    isLoading = false
  }

  private fun onSelectedProcess() {
    if (layoutInspector.processModel?.selectedProcess?.isRunning == true) {
      isLoading = true
    }
    if (layoutInspector.processModel?.selectedProcess == null) {
      isLoading = false
    }
  }

  private fun onInspectorModelChanged(oldWindow: AndroidWindow?, newWindow: AndroidWindow?, isStructuralChange: Boolean) {
    if (oldWindow == null && newWindow != null) {
      isLoading = false
    }
  }
}