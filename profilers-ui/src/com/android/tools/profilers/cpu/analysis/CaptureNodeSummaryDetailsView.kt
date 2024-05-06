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

import com.android.tools.adtui.StatLabel
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CaptureNode
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import com.android.tools.adtui.common.border as BorderColor

class CaptureNodeSummaryDetailsView(profilersView: StudioProfilersView,
                                    tabModel: CaptureNodeAnalysisSummaryTabModel)
  : SummaryDetailsViewBase<CaptureNodeAnalysisSummaryTabModel>(profilersView, tabModel) {
  @get:VisibleForTesting
  val timeRangeLabel = JLabel()

  @get:VisibleForTesting
  val dataTypeLabel = JLabel()

  init {
    val selectionRange = tabModel.selectionRange
    val captureRange = tabModel.captureRange
    timeRangeLabel.text = formatTimeRangeAsString(selectionRange = selectionRange, relativeZeroPoint = captureRange.min.toLong())
    dataTypeLabel.text = tabModel.label

    addRowToCommonSection("Time Range", timeRangeLabel)
    addRowToCommonSection("Data Type", dataTypeLabel)
    addSection(buildSelectedNodeTable())
    // All Occurrences section only applies to single node selection.
    if (tabModel.dataSeries.size == 1) {
      // Asynchronously add this section as it needs expensive data (allOccurrencesStats and tenLongestRunningOccurrences) to be computed.
      profilersView.studioProfilers.ideServices.poolExecutor.execute {
        val model = tabModel.dataSeries[0]
        val allOccurrencesStats = model.allOccurrenceStats
        val tenLongestRunningOccurrences = model.getLongestRunningOccurrences(10)
        profilersView.studioProfilers.ideServices.mainExecutor.execute {
          // We are appending a section to the end of a list of UI components, therefore, we can use this trick to asynchronously update
          // the UI without any disruption to other UI components.
          addSection(buildAllOccurrencesSection(allOccurrencesStats, tenLongestRunningOccurrences))
        }
      }
    }
  }

  private fun buildSelectedNodeTable() = JPanel(TabularLayout("*").setVGap(8)).apply {
    val selectedNodes = tabModel.dataSeries.map { it.node }
    add(JLabel("Selected event".apply {
      font = ProfilerFonts.H3_FONT
      isOpaque = false
    }), TabularLayout.Constraint(0, 0))
    add(CaptureNodeDetailTable(selectedNodes, tabModel.captureRange).component, TabularLayout.Constraint(1, 0))
  }

  private fun buildAllOccurrencesSection(
    allOccurrencesStats: CaptureNodeAnalysisStats,
    tenLongestRunningOccurrences: List<CaptureNode>
  ): JComponent {
    val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      isOpaque = false
      add(JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.customLine(BorderColor, 0, 0, 0, 1)
        add(StatLabel(allOccurrencesStats.count, "Count", numFont = ProfilerFonts.H2_FONT, descFont = ProfilerFonts.H4_FONT).apply {
          isOpaque = false
        })
      })
      // Build StatLabels with the duration formatter for timing stats.
      fun buildDurationLabel(num: Long, desc: String) =
        StatLabel(
          num, desc, numFont = ProfilerFonts.H2_FONT, descFont = ProfilerFonts.H4_FONT,
          numFormatter = TimeFormatter::getSingleUnitDurationString
        ).apply {
          isOpaque = false
        }
      add(buildDurationLabel(allOccurrencesStats.average.toLong(), "Average"))
      add(buildDurationLabel(allOccurrencesStats.max, "Max"))
      add(buildDurationLabel(allOccurrencesStats.min, "Min"))
      add(buildDurationLabel(allOccurrencesStats.standardDeviation.toLong(), "Std Dev"))
    }

    // Table to display longest running occurrences.
    val topOccurrencesLabel = JLabel("Longest running occurrences (select row to navigate)").apply {
      isOpaque = false
      font = ProfilerFonts.H3_FONT
    }
    val topOccurrencesTable = CaptureNodeDetailTable(
      tenLongestRunningOccurrences, tabModel.captureRange,
      profilersView.studioProfilers.stage.timeline.viewRange
    )

    val panel = JPanel(TabularLayout("*", "Fit,Fit,Fit").setVGap(8)).apply {
      isOpaque = false
      add(statsPanel, TabularLayout.Constraint(0, 0))
      add(topOccurrencesLabel, TabularLayout.Constraint(1, 0))
      add(topOccurrencesTable.component, TabularLayout.Constraint(2, 0))
    }

    // Wrap everything in a collapsible panel.
    return HideablePanel.Builder("All Occurrences (${allOccurrencesStats.count})", panel)
      .setPanelBorder(JBUI.Borders.empty())
      .setContentBorder(JBUI.Borders.empty(8, 0, 0, 0))
      .build().apply {
        background = primaryContentBackground
      }
  }
}