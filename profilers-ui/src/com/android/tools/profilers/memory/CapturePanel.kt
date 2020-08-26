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

import com.android.tools.adtui.FilterComponent
import com.android.tools.adtui.StatLabel
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_WIDTH
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_HISTORY_SIZE
import com.android.tools.profilers.ProfilerLayout.TOOLBAR_ICON_BORDER
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.android.tools.profilers.memory.chart.MemoryVisualizationView
import com.google.common.util.concurrent.ListenableFutureTask
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBEmptyBorder
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CapturePanel(profilersView: StudioProfilersView,
                   selection: MemoryCaptureSelection,
                   selectionTimeLabel: JLabel?,
                   selectionRange: Range,
                   ideComponents: IdeProfilerComponents,
                   timeline: StreamingTimeline,
                   isFullScreenHeapDumpUi: Boolean): AspectObserver() {
  val heapView = MemoryHeapView(selection)
  val captureView = MemoryCaptureView(selection, ideComponents) // TODO: remove after full migration. Only needed for legacy tests
  val classGrouping = MemoryClassGrouping(selection)
  val classifierView = MemoryClassifierView(selection, ideComponents)
  val classSetView = MemoryClassSetView(selection, ideComponents, selectionRange, timeline, isFullScreenHeapDumpUi)
  val instanceDetailsView = MemoryInstanceDetailsView(selection, ideComponents, timeline)

  val captureInfoMessage = JLabel(StudioIcons.Common.WARNING).apply {
    border = TOOLBAR_ICON_BORDER
    // preset the minimize size of the info to only show the icon, so the text can be truncated when the user resizes the vertical splitter.
    minimumSize = preferredSize
    isVisible = false
    selection.aspect.addDependency(this@CapturePanel)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        when (val infoMessage = selection.selectedCapture?.infoMessage) {
          null -> isVisible = false
          else -> {
            isVisible = true
            text = infoMessage
            toolTipText = infoMessage
          }
        }
      }
  }

  private val filterComponent =
    FilterComponent(FILTER_TEXT_FIELD_WIDTH, FILTER_TEXT_HISTORY_SIZE, FILTER_TEXT_FIELD_TRIGGER_DELAY_MS).apply {
      model.setFilterHandler(selection.filterHandler)
      border = JBEmptyBorder(0, 4, 0, 0)
    }

  val component =
    if (selection.ideServices.featureConfig.isSeparateHeapDumpUiEnabled)
      CapturePanelUi(selection, heapView, classGrouping, classifierView, filterComponent, captureInfoMessage, profilersView)
    else LegacyCapturePanelUi(selection, selectionTimeLabel,
                              captureView, heapView, classGrouping, classifierView, filterComponent, captureInfoMessage)
}

private class LegacyCapturePanelUi(selection: MemoryCaptureSelection,
                                   selectionTimeLabel: JLabel?,
                                   captureView: MemoryCaptureView,
                                   heapView: MemoryHeapView,
                                   classGrouping: MemoryClassGrouping,
                                   classifierView: MemoryClassifierView,
                                   filterComponent: FilterComponent,
                                   captureInfoMessage: JLabel)
  : JPanel(BorderLayout()) {
  private val instanceFilterView = MemoryInstanceFilterView(selection)
  init {
    val toolbar = JPanel(createToolbarLayout()).apply {
      add(captureView.component)
      add(heapView.component)
      add(classGrouping.component)
      add(instanceFilterView.filterToolbar)
      if (selection.ideServices.featureConfig.isLiveAllocationsSamplingEnabled) {
        add(captureInfoMessage)
      }
    }

    filterComponent.isVisible = false
    val button = FilterComponent.createFilterToggleButton()
    FilterComponent.configureKeyBindingAndFocusBehaviors(this, filterComponent, button)
    val buttonToolbar = JPanel(createToolbarLayout()).apply {
      border = JBEmptyBorder(3, 0, 0, 0)
      selectionTimeLabel?.let { add(it) }
      add(FlatSeparator())
      add(button)
    }
    val headingPanel = JPanel(TabularLayout("Fit,*,Fit")).apply {
      add(filterComponent, TabularLayout.Constraint(2, 0, 3))
      add(buttonToolbar, TabularLayout.Constraint(0, 2))
      add(toolbar, TabularLayout.Constraint(0, 0))
      add(instanceFilterView.filterDescription, TabularLayout.Constraint(1, 0, 3))
    }

    add(headingPanel, BorderLayout.PAGE_START)
    add(classifierView.component, BorderLayout.CENTER)
  }
}

/**
 * Helper class to maintain toolbar components between tabs.
 * One copy of components is maintained to preserve state and manage the selected heap.
 * This provides for a seamless user experience when doing things like filtering.
 * The caveat is components can only be added to one panel at a time. To work around
 * this a list of toolbar components is collected for each tab. When that tab is activated
 * the list of components is added to the selected tab.
 */
private data class ToolbarComponents(val toolbarPanel: JPanel,
                                     val components: List<Component>)

private class CapturePanelUi(private val selection: MemoryCaptureSelection,
                             private val heapView: MemoryHeapView,
                             private val classGrouping: MemoryClassGrouping,
                             private val classifierView: MemoryClassifierView,
                             private val filterComponent: FilterComponent,
                             private val captureInfoMessage: JLabel,
                             private val profilersView: StudioProfilersView)
  : JPanel(BorderLayout()) {
  private val observer = AspectObserver()
  private val instanceFilterMenu = MemoryInstanceFilterMenu(selection)
  private val toolbarTabPanels = mutableMapOf<String, ToolbarComponents>()
  private val tabListeners = mutableListOf<CapturePanelTabContainer>()
  private val visualizationView = MemoryVisualizationView(selection, profilersView)
  private var activeTabIndex = 0

  init {
    val headingPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(buildSummaryPanel())
    }
    add(headingPanel, BorderLayout.PAGE_START)
    add(buildDetailsPanel(headingPanel), BorderLayout.CENTER)
  }

  private fun buildDetailsPanel(headingPanel: JPanel) = JPanel(BorderLayout()).apply {
    fun refreshPanel() {
      removeAll()
      if (selection.selectedCapture is HeapDumpCaptureObject) {
        val toolbarPanel = JPanel(createToolbarLayout())
        toolbarDefaults().forEach { toolbarPanel.add(it) }
        headingPanel.add(buildToolbarPanel(toolbarPanel), 0)
        add(classifierView.component)
      }
      else {
        add(buildTabPanel(), BorderLayout.CENTER)
      }
    }
    selection.aspect.addDependency(observer).onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, ::refreshPanel)
    refreshPanel()
  }

  private fun buildNonTabPanel(toolbar: JPanel, component: JComponent) = JPanel(BorderLayout()).apply {
    add(buildToolbarPanel(toolbar), BorderLayout.PAGE_START)
    add(component, BorderLayout.CENTER)
  }

  // Add the right side toolbar so that it is on top of the truncated |myCaptureInfoMessage|.
  private fun buildTabPanel() = JBTabbedPane().apply {
    addTab(this, "Table", classifierView, toolbarDefaults())
    addTab(this, "Visualization", visualizationView, mutableListOf(visualizationView.toolbarComponents, toolbarCore()).flatten())
    fun updateTabs() {
      // do move which panel the tabs bar appears on.
      tabListeners[activeTabIndex].onSelectionChanged(false)
      val title = getTitleAt(selectedIndex)
      val panel = toolbarTabPanels[title]!!.toolbarPanel
      panel.removeAll()
      toolbarTabPanels[title]!!.components.forEach { panel.add(it) }
      tabListeners[selectedIndex].onSelectionChanged(true)
      activeTabIndex = selectedIndex
    }
    addChangeListener { updateTabs() }
    updateTabs()
  }

  private fun addTab(tabPane: JBTabbedPane, name: String, tabContainer: CapturePanelTabContainer, toolbarComponents: List<Component>) {
    toolbarTabPanels[name] = ToolbarComponents(JPanel(createToolbarLayout()), toolbarComponents)
    tabListeners.add(tabContainer)
    tabPane.add(name, buildNonTabPanel(toolbarTabPanels[name]!!.toolbarPanel, tabContainer.component))
  }

  private fun toolbarDefaults() = mutableListOf<Component>().apply {
    if (!(selection.selectedCapture is NativeAllocationSampleCaptureObject)) {
      add(heapView.component)
    }
    add(classGrouping.component)
    addAll(toolbarCore())
  }

  private fun toolbarCore() = mutableListOf<Component>().apply {
    add(instanceFilterMenu.component)
    add(filterComponent)
    if (selection.ideServices.featureConfig.isLiveAllocationsSamplingEnabled) {
      add(captureInfoMessage)
    }
  }

  private fun buildToolbarPanel(toolbar: JPanel) = JPanel(BorderLayout()).apply {
    add(toolbar, BorderLayout.LINE_START)
    alignmentX = Component.LEFT_ALIGNMENT
    minimumSize = Dimension(0, minimumSize.height)
  }

  private fun buildSummaryPanel() = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    fun mkLabel(desc: String, action: Runnable? = null) =
      StatLabel(0L, desc, numFont = ProfilerFonts.H2_FONT, descFont = AdtUiUtils.DEFAULT_FONT.biggerOn(1f), action = action)
    val totalClassLabel = mkLabel("Classes")
    val totalLeakLabel = mkLabel("Leaks", action = Runnable(::showLeaks))
    val totalCountLabel = mkLabel("Count")
    val totalNativeSizeLabel = mkLabel("Native Size")
    val totalShallowSizeLabel = mkLabel("Shallow Size")
    val totalRetainedSizeLabel = mkLabel("Retained Size")

    // Compute total classes asynchronously because it can take multiple seconds
    fun refreshTotalClassesAsync(heap: HeapSet) = profilersView.studioProfilers.ideServices.poolExecutor.execute {
      // Handle "no filter" case specially, because it recomputes from the current instance stream,
      // and `ClassifierSet` only considers instances as "matched" if the filter is not empty.
      // This is analogous to how `MemoryClassifierView` is checking if filter is empty to treat it specially
      val filterMatches = if (selection.filterHandler.filter.isEmpty) heap.instancesStream else heap.filterMatches
      // Totals other than class count don't need this, because they are direct fields initialized correctly
      val count = filterMatches.mapToLong { it.classEntry.classId }.distinct().count()
      profilersView.studioProfilers.ideServices.mainExecutor.execute { totalClassLabel.numValue = count }
    }

    fun refreshSummaries() {
      selection.selectedHeapSet?.let { heap ->
        refreshTotalClassesAsync(heap)
        totalCountLabel.numValue = heap.totalObjectCount.toLong()
        totalNativeSizeLabel.numValue = heap.totalNativeSize
        totalShallowSizeLabel.numValue = heap.totalShallowSize
        totalRetainedSizeLabel.numValue = heap.totalRetainedSize

        selection.selectedCapture?.let { capture ->
          isVisible = capture is HeapDumpCaptureObject
          when (val filter = capture.activityFragmentLeakFilter) {
            null -> totalLeakLabel.isVisible = false
            else -> totalLeakLabel.apply {
              val leakCount = heap.getInstanceFilterMatchCount(filter).toLong()
              isVisible = true
              numValue = leakCount
              icon = if (leakCount > 0) StudioIcons.Common.WARNING else null
            }
          }
        }
      }
    }

    selection.aspect.addDependency(observer)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS, ::refreshSummaries)
      .onChange(CaptureSelectionAspect.CURRENT_FILTER, ::refreshSummaries)

    add(totalClassLabel)
    add(totalLeakLabel)
    add(FlatSeparator(6, 36))
    add(totalCountLabel)
    add(totalNativeSizeLabel)
    add(totalShallowSizeLabel)
    add(totalRetainedSizeLabel)
    alignmentX = Component.LEFT_ALIGNMENT
  }

  private fun showLeaks() {
    selection.selectedCapture?.activityFragmentLeakFilter?.let {
      instanceFilterMenu.component.selectedItem = it
    }
  }
}