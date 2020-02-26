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
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_TRIGGER_DELAY_MS
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_FIELD_WIDTH
import com.android.tools.profilers.ProfilerLayout.FILTER_TEXT_HISTORY_SIZE
import com.android.tools.profilers.ProfilerLayout.TOOLBAR_ICON_BORDER
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.memory.adapters.ClassifierSet
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.util.stream.Collectors
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class CapturePanel(stageView: MemoryProfilerStageView): AspectObserver() {
  private val myStage = stageView.stage
  private val instanceFilterView = MemoryInstanceFilterView(myStage)
  val captureView = MemoryCaptureView(myStage, stageView.ideComponents)
  val heapView = MemoryHeapView(myStage)
  val classGrouping = MemoryClassGrouping(myStage)
  val classifierView = MemoryClassifierView(myStage, stageView.ideComponents)
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

  val component = JPanel(BorderLayout()).apply {
    val toolbar = JPanel(createToolbarLayout()).apply {
      add(captureView.component)
      add(heapView.component)
      add(classGrouping.component)
      add(instanceFilterView.filterToolbar)
      if (myStage.studioProfilers.ideServices.featureConfig.isLiveAllocationsSamplingEnabled) {
        add(captureInfoMessage)
      }
    }

    val filterComponent =
      FilterComponent(FILTER_TEXT_FIELD_WIDTH, FILTER_TEXT_HISTORY_SIZE, FILTER_TEXT_FIELD_TRIGGER_DELAY_MS).apply {
        model.setFilterHandler(myStage.filterHandler)
        border = JBEmptyBorder(0, 4, 0, 0)
        isVisible = false
      }
    val button = FilterComponent.createFilterToggleButton()
    FilterComponent.configureKeyBindingAndFocusBehaviors(this, filterComponent, button)
    val buttonToolbar = JPanel(createToolbarLayout()).apply {
      border = JBEmptyBorder(3, 0, 0, 0)
      if (!myStage.isMemoryCaptureOnly) {
        add(stageView.selectionTimeLabel)
      }
      add(FlatSeparator())
      add(button)
    }
    val legacyHeadingPanel = JPanel(TabularLayout("Fit,*,Fit")).apply {
      add(filterComponent, TabularLayout.Constraint(2, 0, 3))
      add(buttonToolbar, TabularLayout.Constraint(0, 2))
      add(toolbar, TabularLayout.Constraint(0, 0))
      add(instanceFilterView.filterDescription, TabularLayout.Constraint(1, 0, 3))
    }

    val headingPanel =
      if (myStage.studioProfilers.ideServices.featureConfig.isSeparateHeapDumpUiEnabled)
        JPanel().apply {
          layout = BoxLayout(this, BoxLayout.Y_AXIS)
          add(JBLabel("Totals across all heap dumps", SwingConstants.LEFT).apply {
            border = JBEmptyBorder(4, 12, 0, 0)
            alignmentX = Component.LEFT_ALIGNMENT
          })
          add(buildSummaryPanel())
          add(legacyHeadingPanel)
        }
      else legacyHeadingPanel

    add(headingPanel, BorderLayout.PAGE_START)
    add(classifierView.component, BorderLayout.CENTER)
  }

  private fun buildSummaryPanel() = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    val totalClassLabel = StatLabel(0, "Total Classes")
    val totalLeakLabel = StatLabel(0, "Total Leaks", Runnable(::showLeaks))
    val totalCountLabel = StatLabel(0, "Total Count")
    val totalNativeSizeLabel = StatLabel(0, "Total Native Size")
    val totalShallowSizeLabel = StatLabel(0, "Total Shallow Size")
    val totalRetainedSizeLabel = StatLabel(0, "Total Retained Size")

    myStage.aspect.addDependency(this@CapturePanel)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS) {
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

    add(totalClassLabel)
    add(totalLeakLabel)
    add(FlatSeparator(6, 36))
    add(totalCountLabel)
    add(totalNativeSizeLabel)
    add(totalShallowSizeLabel)
    add(totalRetainedSizeLabel)
    alignmentX = Component.LEFT_ALIGNMENT
  }

  private fun showLeaks() {} // TODO(b/149595994) implement always-filtering

  private fun countClasses() = // count distinct class Ids across all heap sets
    myStage.selectedCapture!!.heapSets.stream().flatMap {hs ->
      hs.instancesStream.map{it.classEntry.classId}
    }.collect(Collectors.toSet()).size.toLong()

  private fun countLeaks(): Int? = null // no support for always-filtering for now

  private fun setLabelSumBy(label: StatLabel, prop: (ClassifierSet) -> Long) {
    label.intContent = myStage.selectedCapture!!.heapSets.fold(0L){sum, heapSet -> sum+prop(heapSet)}
  }
}