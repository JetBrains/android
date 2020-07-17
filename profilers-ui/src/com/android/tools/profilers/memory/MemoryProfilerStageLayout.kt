/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.intellij.ui.JBSplitter
import javax.swing.JComponent
import javax.swing.JPanel


/**
 * This class abstracts away the laying out of (@link MemoryProfilerStageView}
 */
class MemoryProfilerStageLayout(private val timelineView: JComponent?,
                                val capturePanel: CapturePanel,
                                private val makeLoadingPanel: () -> LoadingPanel) {
  val toolbar = JPanel(createToolbarLayout())

  protected var loadingPanel: LoadingPanel? = null

  var isLoadingUiVisible: Boolean
    get() = loadingPanel != null
    set(isShown) {
      val currentLoadingPanel = loadingPanel
      when {
        currentLoadingPanel == null && isShown -> {
          val newLoadingPanel = makeLoadingPanel()
          loadingPanel = newLoadingPanel
          newLoadingPanel.startLoading()
          chartCaptureSplitter.secondComponent = newLoadingPanel.component
        }
        currentLoadingPanel != null && !isShown -> {
          currentLoadingPanel.stopLoading()
          chartCaptureSplitter.secondComponent = null
          loadingPanel = null
        }
      }
    }

  private val instanceDetailsSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = capturePanel.classSetView.component
    secondComponent = capturePanel.instanceDetailsView.component
  }
  val chartCaptureSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = timelineView
  }
  val mainSplitter = JBSplitter(false).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = chartCaptureSplitter
    secondComponent = instanceDetailsSplitter
    proportion = .6f
  }

  val component: JComponent
    get() = mainSplitter

  var isShowingCaptureUi = false
    set(isShown) {
      field = isShown
      chartCaptureSplitter.secondComponent = if (isShown) capturePanel.component else null
    }
}