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

import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BORDER_COLOR
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.CloseButton
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel


/**
 * This class abstracts away the laying out of (@link MemoryProfilerStageView},
 * responding differently to requests showing/hiding the heap-dump.
 * The legacy one uses JBSplitters to share the heap-dump view with the main timeline.
 * The new one uses a CardLayout that swaps out the timeline for the heap-dump view.
 * This is a temporary solution for maintaining both the new and legacy UIs.
 * The right solution in the long run should be separating the heap-dump out into its own stage.
 */
internal abstract class MemoryProfilerStageLayout(val capturePanel: CapturePanel,
                                                  private val loadingPanel: LoadingPanel) {
  abstract val component: JComponent
  val toolbar = JPanel(createToolbarLayout())


  var isShowingLoadingUi: Boolean = false
    set(isShown) {
      field = isShown
      if (isShown) {
        loadingPanel.startLoading()
        showLoadingView()
      } else {
        loadingPanel.stopLoading()
        hideLoadingView()
      }
    }
  abstract fun showCaptureUi(isShown: Boolean)

  protected val myLoadingView = loadingPanel.component
  protected abstract fun showLoadingView()
  protected abstract fun hideLoadingView()

  // TODO: Below are just for legacy tests. Remove them once fully migrated to new Ui
  abstract val chartCaptureSplitter: Splitter
  abstract val mainSplitter: Splitter
}

internal class SeparateHeapDumpMemoryProfilerStageLayout(timelineView: JComponent?,
                                                         capturePanel: CapturePanel,
                                                         loadingPanel: LoadingPanel,
                                                         private val myStage: MemoryProfilerStage,
                                                         private val myTimelineShowingCallback: Runnable,
                                                         private val myCaptureShowingCallback: Runnable,
                                                         private val myLoadingShowingCallback: Runnable)
      : MemoryProfilerStageLayout(capturePanel, loadingPanel) {
  private val myLayout = CardLayout()
  private val myTitle = JBLabel().apply {
    border = JBEmptyBorder(0, 5, 0, 0)
  }

  private val instanceDetailsSplitter = JBSplitter(false).apply {
    isOpaque = true
    firstComponent = capturePanel.classSetView.component
    secondComponent = capturePanel.instanceDetailsView.component
  }

  private val instanceDetailsWrapper = JBPanel<Nothing>(BorderLayout()).apply {
    val headingPanel = JPanel(BorderLayout()).apply {
      border = DEFAULT_HORIZONTAL_BORDERS
      add(myTitle, BorderLayout.WEST)
      add(CloseButton{ myStage.selectClassSet(null) }, BorderLayout.EAST)
    }
    add(headingPanel, BorderLayout.NORTH)
    add(instanceDetailsSplitter, BorderLayout.CENTER)
  }

  override val chartCaptureSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = capturePanel.component
    secondComponent = instanceDetailsWrapper
  }

  override val component = JPanel(myLayout).apply {
    add(timelineView, CARD_TIMELINE)
    add(chartCaptureSplitter, CARD_CAPTURE)
    add(loadingPanel.component, CARD_LOADING)
    myLayout.show(this, CARD_TIMELINE)
  }

  private val myObserver = AspectObserver()

  init {
    myStage.aspect.addDependency(myObserver)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, ::updateInstanceDetailsSplitter)
    updateInstanceDetailsSplitter()
  }

  override fun showCaptureUi(isShown: Boolean) {
    if (isShown) {
      myLayout.show(component, CARD_CAPTURE)
      myCaptureShowingCallback.run()
    } else {
      myLayout.show(component, CARD_TIMELINE)
      myTimelineShowingCallback.run()
    }
  }

  override fun showLoadingView() {
    myLayout.show(component, CARD_LOADING)
    myLoadingShowingCallback.run()
  }

  override fun hideLoadingView() = myLayout.show(component, CARD_TIMELINE)

  override val mainSplitter: Splitter
    get() = TODO("remove on full migration")

  private fun updateInstanceDetailsSplitter() = when (val cs = myStage.selectedClassSet) {
    null -> instanceDetailsWrapper.isVisible = false
    else -> {
      myTitle.text = "Instance List - " + cs.name
      instanceDetailsWrapper.isVisible = true
    }
  }

  private companion object {
    const val CARD_TIMELINE = "timeline"
    const val CARD_CAPTURE = "capture"
    const val CARD_LOADING = "loading"
  }
}

internal class LegacyMemoryProfilerStageLayout(timelineView: JComponent?,
                                               capturePanel: CapturePanel,
                                               loadingPanel: LoadingPanel)
      : MemoryProfilerStageLayout(capturePanel, loadingPanel) {
  private val instanceDetailsSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = capturePanel.classSetView.component
    secondComponent = capturePanel.instanceDetailsView.component
  }
  override val chartCaptureSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = timelineView
  }
  override val mainSplitter = JBSplitter(false).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = chartCaptureSplitter
    secondComponent = instanceDetailsSplitter
    proportion = .6f
  }

  override fun showCaptureUi(isShown: Boolean) {
    chartCaptureSplitter.secondComponent = if (isShown) capturePanel.component else null
  }

  override fun showLoadingView() {
    chartCaptureSplitter.secondComponent = myLoadingView
  }

  override fun hideLoadingView() {
    chartCaptureSplitter.secondComponent = null
  }

  override val component: JComponent
    get() = mainSplitter
}
