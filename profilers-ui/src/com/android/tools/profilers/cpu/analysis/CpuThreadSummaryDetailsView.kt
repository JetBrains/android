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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.StudioProfilersView
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import com.android.tools.adtui.common.border as BorderColor

class CpuThreadSummaryDetailsView(parentView: StudioProfilersView,
                                  tabModel: CpuThreadAnalysisSummaryTabModel) : SummaryDetailsViewBase<CpuThreadAnalysisSummaryTabModel>(
  parentView, tabModel) {
  @get:VisibleForTesting
  val timeRangeLabel = JLabel()

  @get:VisibleForTesting
  val durationLabel = JLabel()

  @get:VisibleForTesting
  val dataTypeLabel = JLabel(tabModel.label)

  @get:VisibleForTesting
  val threadIdLabel = JLabel()

  private val nodesTablePanel = JPanel(BorderLayout())

  init {
    // Common section
    addRowToCommonSection("Time Range", timeRangeLabel)
    addRowToCommonSection("Duration", durationLabel)
    addRowToCommonSection("Data Type", dataTypeLabel)
    if (tabModel.dataSeries.size == 1) {
      // Only display thread ID when a single thread is selected.
      addRowToCommonSection("ID", threadIdLabel.apply { text = tabModel.dataSeries[0].threadInfo.id.toString() })
    }
    tabModel.selectionRange.addDependency(observer).onChange(Range.Aspect.RANGE) { onSelectionRangeChanged() }
    onSelectionRangeChanged()

    // Thread states section
    // Merge thread state data series from all threads.
    tabModel.dataSeries.mapNotNull { it.threadStateSeries }.let { threadStateSeriesList ->
      if (threadStateSeriesList.isNotEmpty()) {
        addSection(CpuThreadStateTable(parentView.studioProfilers, threadStateSeriesList, tabModel.selectionRange).component)
      }
    }
    addSection(nodesTablePanel)
  }

  private fun onSelectionRangeChanged() {
    val range = tabModel.selectionRange
    timeRangeLabel.text = formatTimeRangeAsString(range)
    durationLabel.text = TimeFormatter.getSingleUnitDurationString(range.length.toLong())
    populateNodesTable()
  }

  private fun populateNodesTable() {
    profilersView.studioProfilers.ideServices.poolExecutor.execute {
      val nodesInRange = tabModel.getTopNodesInSelectionRange(NUMBER_OF_TABLE_NODES)
      profilersView.studioProfilers.ideServices.mainExecutor.execute {
        nodesTablePanel.removeAll()
        if (nodesInRange.isNotEmpty()) {
          val nodesTable = CaptureNodeDetailTable(nodesInRange, tabModel.captureRange,
                                                  profilersView.studioProfilers.stage.timeline.viewRange)
          val sizeText = if (nodesInRange.size == NUMBER_OF_TABLE_NODES) "top ${NUMBER_OF_TABLE_NODES}" else "${nodesInRange.size}"
          val contentBorder = JBUI.Borders.merge(JBUI.Borders.customLine(BorderColor, 1), JBUI.Borders.empty(8, 0, 0, 0), true)
          val hideablePanel = HideablePanel.Builder("Longest running events (${sizeText})", nodesTable.component)
            .setPanelBorder(JBUI.Borders.empty())
            .setContentBorder(contentBorder)
            .build()
            .apply {
              background = primaryContentBackground
            }
          nodesTablePanel.add(hideablePanel)
        }
        nodesTablePanel.invalidate()
        nodesTablePanel.repaint()
      }
    }
  }

  companion object {
    private const val NUMBER_OF_TABLE_NODES = 10
  }
}