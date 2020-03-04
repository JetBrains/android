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
import com.android.tools.adtui.flat.FlatSeparator
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_WIDTH
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_HISTORY_SIZE
import com.android.tools.profilers.ProfilerLayout.TOOLBAR_ICON_BORDER
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.intellij.util.ui.JBEmptyBorder
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.util.stream.Collectors
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

internal class CapturePanel(stageView: MemoryProfilerStageView): AspectObserver() {
  private val myStage = stageView.stage
  val heapView = MemoryHeapView(myStage)
  val captureView = MemoryCaptureView(stageView.stage, stageView.ideComponents) // TODO: remove after full migration. Only needed for legacy tests
  val classGrouping = MemoryClassGrouping(myStage)
  val classifierView = MemoryClassifierView(myStage, stageView.ideComponents)
  val classSetView = MemoryClassSetView(myStage, stageView.ideComponents)
  val instanceDetailsView = MemoryInstanceDetailsView(myStage, stageView.ideComponents)

  val captureInfoMessage = JLabel(StudioIcons.Common.WARNING).apply {
    border = TOOLBAR_ICON_BORDER
    // preset the minimize size of the info to only show the icon, so the text can be truncated when the user resizes the vertical splitter.
    minimumSize = preferredSize
    isVisible = false
    myStage.aspect.addDependency(this@CapturePanel)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS) {
        when (val infoMessage = myStage.selectedCapture?.infoMessage) {
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
      model.setFilterHandler(myStage.filterHandler)
      border = JBEmptyBorder(0, 4, 0, 0)
    }

  val component =
    if (myStage.studioProfilers.ideServices.featureConfig.isSeparateHeapDumpUiEnabled)
      CapturePanelUi(stageView.stage, heapView, classGrouping, classifierView, filterComponent, captureInfoMessage)
    else LegacyCapturePanelUi(stageView, captureView, heapView, classGrouping, classifierView, filterComponent, captureInfoMessage)
}

private class LegacyCapturePanelUi(stageView: MemoryProfilerStageView,
                                   captureView: MemoryCaptureView,
                                   heapView: MemoryHeapView,
                                   classGrouping: MemoryClassGrouping,
                                   classifierView: MemoryClassifierView,
                                   filterComponent: FilterComponent,
                                   captureInfoMessage: JLabel)
  : JPanel(BorderLayout()) {
  init {
    val instanceFilterView = MemoryInstanceFilterView(stageView.stage)
    val toolbar = JPanel(createToolbarLayout()).apply {
      add(captureView.component)
      add(heapView.component)
      add(classGrouping.component)
      add(instanceFilterView.filterToolbar)
      if (stageView.stage.studioProfilers.ideServices.featureConfig.isLiveAllocationsSamplingEnabled) {
        add(captureInfoMessage)
      }
    }

    filterComponent.isVisible = false
    val button = FilterComponent.createFilterToggleButton()
    FilterComponent.configureKeyBindingAndFocusBehaviors(this, filterComponent, button)
    val buttonToolbar = JPanel(createToolbarLayout()).apply {
      border = JBEmptyBorder(3, 0, 0, 0)
      if (!stageView.stage.isMemoryCaptureOnly) {
        add(stageView.selectionTimeLabel)
      }
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

private class CapturePanelUi(private val myStage: MemoryProfilerStage,
                             heapView: MemoryHeapView,
                             classGrouping: MemoryClassGrouping,
                             classifierView: MemoryClassifierView,
                             filterComponent: FilterComponent,
                             captureInfoMessage: JLabel)
      : JPanel(BorderLayout()) {
  private val myObserver = AspectObserver()
  private val myInstanceFilterMenu = MemoryInstanceFilterMenu(myStage)

  init {
    val toolbar = JPanel(createToolbarLayout()).apply {
      add(heapView.component)
      add(classGrouping.component)
      add(myInstanceFilterMenu.component)
      add(filterComponent)
      if (myStage.studioProfilers.ideServices.featureConfig.isLiveAllocationsSamplingEnabled) {
        add(captureInfoMessage)
      }
    }

    // Add the right side toolbar so that it is on top of the truncated |myCaptureInfoMessage|.
    val toolbarPanel = JPanel(TabularLayout("Fit,*,Fit")).apply {
      add(toolbar, TabularLayout.Constraint(0, 0))
      alignmentX = Component.LEFT_ALIGNMENT
    }

    val headingPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(toolbarPanel)
      add(buildSummaryPanel())
    }

    add(headingPanel, BorderLayout.PAGE_START)
    add(classifierView.component, BorderLayout.CENTER)
  }

  private fun buildSummaryPanel() = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    fun mkLabel(desc: String, action: Runnable? = null) =
      StatLabel(0, desc, numFont = ProfilerFonts.H2_FONT, descFont = ProfilerFonts.H4_FONT, action = action)
    val totalClassLabel = mkLabel("Classes")
    val totalLeakLabel = mkLabel("Leaks", action = Runnable(::showLeaks))
    val totalCountLabel = mkLabel("Count")
    val totalNativeSizeLabel = mkLabel("Native Size")
    val totalShallowSizeLabel = mkLabel("Shallow Size")
    val totalRetainedSizeLabel = mkLabel("Retained Size")

    fun refreshSummaries() {
      totalClassLabel.intContent = countClasses()
      setLabelSumBy(totalCountLabel) {it.totalObjectCount.toLong()}
      setLabelSumBy(totalNativeSizeLabel) {it.totalNativeSize}
      setLabelSumBy(totalShallowSizeLabel) {it.totalShallowSize}
      setLabelSumBy(totalRetainedSizeLabel) {it.totalRetainedSize}

      // Only show "leak" stat when it's supported
      when (val leakCount = countLeaks()) {
        null -> totalLeakLabel.isVisible = false
        else -> totalLeakLabel.apply {
          isVisible = true
          intContent = leakCount.toLong()
          icon = if (leakCount > 0) StudioIcons.Common.WARNING else null
        }
      }
    }

    myStage.aspect.addDependency(myObserver)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, ::refreshSummaries)
      .onChange(MemoryProfilerAspect.CURRENT_FILTER) {
        if (myStage.selectedCapture != null)
          refreshSummaries()
      }

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
    val filter = myStage.selectedCapture!!.findLeakFilter()
    if (filter != null) {
      myInstanceFilterMenu.component.selectedItem = filter
    }
  }

  private fun countClasses() = // count distinct class Ids across all heap sets
    myStage.selectedCapture!!.heapSets.stream().flatMap {hs ->
      hs.instancesStream.map{it.classEntry.classId}
    }.collect(Collectors.toSet()).size.toLong()

  private fun countLeaks(): Int? {
    val captureObject = myStage.selectedCapture!!
    return captureObject.findLeakFilter()
      ?.filter(captureObject.instances.collect(Collectors.toSet()), captureObject.classDatabase)
      ?.size
  }

  private fun setLabelSumBy(label: StatLabel, prop: (ClassifierSet) -> Long) {
    label.intContent = myStage.selectedCapture!!.heapSets.fold(0L){sum, heapSet -> sum+prop(heapSet)}
  }

  private companion object {
    fun CaptureObject.findLeakFilter() =
      (supportedInstanceFilters.find { it is ActivityFragmentLeakInstanceFilter })
        as ActivityFragmentLeakInstanceFilter?
  }
}