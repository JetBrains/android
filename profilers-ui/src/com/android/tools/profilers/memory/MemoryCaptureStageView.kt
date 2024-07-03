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

import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.stdui.CloseButton
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.CardLayout
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPanel


class MemoryCaptureStageView(profilersView: StudioProfilersView, stage: MemoryCaptureStage)
      : BaseMemoryProfilerStageView<MemoryCaptureStage>(profilersView, stage) {

  private val capturePanel = CapturePanel(profilersView,
                                          stage.captureSelection,
                                          null,
                                          stage.studioProfilers.timeline.selectionRange,
                                          ideComponents,
                                          stage.timeline,
                                          true)

  private val title = JBLabel().apply {
    border = JBEmptyBorder(0, 5, 0, 0)
  }

  private var loadingPanel: LoadingPanel? = null

  private val instanceDetailsSplitter = JBSplitter(false).apply {
    isOpaque = true
    firstComponent = capturePanel.classSetView.component
    secondComponent = capturePanel.instanceDetailsView.component
  }

  private val instanceDetailsWrapper = JBPanel<Nothing>(BorderLayout()).apply {
    val headingPanel = JPanel(BorderLayout()).apply {
      border = DEFAULT_HORIZONTAL_BORDERS
      add(title, BorderLayout.WEST)
      add(CloseButton { stage.captureSelection.selectClassSet(null) }, BorderLayout.EAST)
    }
    add(headingPanel, BorderLayout.NORTH)
    add(instanceDetailsSplitter, BorderLayout.CENTER)
  }

  private val chartCaptureSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = capturePanel.component
    secondComponent = instanceDetailsWrapper
  }

  private val layout = CardLayout()
  private val mainPanel = JPanel(layout).apply {
    add(chartCaptureSplitter, CARD_CAPTURE)
  }

  init {
    fun updateInstanceDetailsSplitter() = when (val cs = stage.captureSelection.selectedClassSet) {
      null -> instanceDetailsWrapper.isVisible = false
      else -> {
        title.text = "Instance List - " + cs.name
        instanceDetailsWrapper.isVisible = true
      }
    }

    stage.captureSelection.aspect.addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_CLASS, ::updateInstanceDetailsSplitter)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, ::showCaptureUi)
    updateInstanceDetailsSplitter()
    showLoadingPanel()
    component.add(mainPanel)

    // Also stop loading panel if the session is terminated before successful loading (b/159247100)
    mainPanel.addHierarchyListener {
      if (!mainPanel.isDisplayable || !mainPanel.isShowing) {
        hideLoadingPanel()
      }
    }
  }

  // If Task-Based UX is enabled, no toolbar is added to the memory capture stage view.
  override fun isToolbarVisible() = !stage.studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled

  override fun getToolbar() = JPanel(BorderLayout()).apply {
    add(JLabel().apply {
      border = JBEmptyBorder(0, 4, 0, 2)
      text = when (val capture = stage.captureSelection.selectedCapture) {
        null -> "Heap Dump"
        else -> {
          val startMicros = stage.timeline.dataRange.min.toLong()
          val elapsedMicros = TimeUnit.NANOSECONDS.toMicros(capture.startTimeNs) - startMicros
          // b/156547059
          // It's plausible, but highly unlikely that `elapsedMicros` is 0 for a recorded heap dump.
          // Often this happens when it's loaded from a file. Because it's difficult to get the filename
          // from here, we simply omit the misleading information
          if (elapsedMicros > 0)
            "${capture.name}: ${TimeFormatter.getSimplifiedClockString(elapsedMicros)}"
          else "${capture.name} (loaded from file)"
        }
      }
    }, BorderLayout.WEST)
  }

  private fun showCaptureUi() {
    hideLoadingPanel()
    layout.show(mainPanel, CARD_CAPTURE)
  }

  private fun showLoadingPanel() {
    if (loadingPanel == null)
      makeLoadingPanel().let {
        loadingPanel = it
        it.startLoading()
        mainPanel.add(it.component, CARD_LOADING)
        layout.show(mainPanel, CARD_LOADING)
      }
  }

  private fun hideLoadingPanel() {
    loadingPanel?.let {
      it.stopLoading()
      mainPanel.remove(it.component)
      loadingPanel = null
    }
  }

  private companion object {
    const val CARD_CAPTURE = "capture"
    const val CARD_LOADING = "loading"
  }
}
