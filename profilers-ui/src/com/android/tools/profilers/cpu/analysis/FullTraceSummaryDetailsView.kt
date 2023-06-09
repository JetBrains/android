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

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.ValueFormattingUtils.formatLongValueWithCommas
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeCumulativePowerUsageInRange
import com.android.tools.profilers.cpu.systemtrace.PowerRailTrackModel.Companion.POWER_RAIL_UNIT
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import javax.swing.JLabel

class FullTraceSummaryDetailsView(profilersView: StudioProfilersView,
                                  tabModel: FullTraceAnalysisSummaryTabModel) : SummaryDetailsViewBase<FullTraceAnalysisSummaryTabModel>(
  profilersView, tabModel) {
  @get: VisibleForTesting
  val timeRangeLabel = JLabel()

  @get: VisibleForTesting
  val durationLabel = JLabel()

  @get: VisibleForTesting
  val powerUsedLabel = JLabel()

  init {
    addRowToCommonSection("Time Range", timeRangeLabel)
    addRowToCommonSection("Duration", durationLabel)
    addRowToCommonSectionWithInfoIcon("Total Power Used in Range", powerUsedLabel, POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG)
    tabModel.selectionRange.addDependency(observer).onChange(Range.Aspect.RANGE) { updateRangeLabels() }
    updateRangeLabels()
    // The CpuCapture containing the system trace data is always the first element in the tab model's data series
    val cpuCapture = tabModel.dataSeries[0]
    cpuCapture.systemTraceData?.powerRailCounters?.let { powerRailCounters ->
      if (powerRailCounters.isNotEmpty()) {
        addSection(
          PowerRailTable(profilersView.studioProfilers, powerRailCounters, tabModel.selectionRange, tabModel.captureRange).component)
      }
    }
    // Add a collapsible Help Text section containing Navigation and Analysis instructions (initially collapsed)
    addSection(HideablePanel.Builder(HelpTextView.HELP_TEXT_TITLE, HelpTextView()).setInitiallyExpanded(false).setPanelBorder(
      JBUI.Borders.empty()).build())
  }

  private fun updateRangeLabels() {
    val selectionRange = tabModel.selectionRange
    val captureRange = tabModel.captureRange
    timeRangeLabel.text = formatTimeRangeAsString(selectionRange = selectionRange, relativeZeroPoint = captureRange.min.toLong())
    durationLabel.text = TimeFormatter.getSingleUnitDurationString(selectionRange.length.toLong())

    var totalPowerUws = 0L
    // The CpuCapture containing the system trace data is always the first element in the tab model's data series
    val cpuCapture = tabModel.dataSeries[0]
    val powerRailCounters = cpuCapture.systemTraceData?.powerRailCounters
    powerRailCounters?.forEach {
      totalPowerUws += computeCumulativePowerUsageInRange(it.value.cumulativeData, selectionRange)
    }

    powerUsedLabel.text = "${formatLongValueWithCommas(totalPowerUws)} $POWER_RAIL_UNIT"
  }
}