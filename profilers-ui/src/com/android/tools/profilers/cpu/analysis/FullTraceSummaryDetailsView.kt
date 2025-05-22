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
import com.android.tools.profilers.StringFormattingUtils.formatLongValueWithCommas
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeCumulativeEnergyInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computePowerUsageRange
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
  val energyUsedLabel = JLabel()

  @get: VisibleForTesting
  var powerRailTable: PowerRailTable? = null

  init {
    addRowToCommonSection("Time Range", timeRangeLabel)
    addRowToCommonSection("Duration", durationLabel)
    // The CpuCapture containing the system trace data is always the first element in the tab model's data series
    val cpuCapture = tabModel.dataSeries[0]
    cpuCapture.systemTraceData?.powerRailCounters?.let { powerRailCounters ->
      if (powerRailCounters.isNotEmpty()) {
        addRowToCommonSectionWithInfoIcon("Total Energy Used in Range", energyUsedLabel, POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG)
        powerRailTable = PowerRailTable(profilersView.studioProfilers, powerRailCounters, tabModel.selectionRange,
                                            tabModel.captureRange)
        addSection(powerRailTable!!.component)
      }
    }
    // Add a collapsible Usage Instructions section containing Navigation and Analysis instructions (initially collapsed)
    addSection(HideablePanel.Builder(UsageInstructionsView.USAGE_INSTRUCTIONS_TITLE, UsageInstructionsView()).setInitiallyExpanded(false).setPanelBorder(
      JBUI.Borders.empty()).build().apply { background = primaryContentBackground })

    tabModel.selectionRange.addDependency(observer).onChange(Range.Aspect.RANGE) { updateRangeLabels() }
    updateRangeLabels()
  }

  private fun updateRangeLabels() {
    val selectionRange = tabModel.selectionRange
    val captureRange = tabModel.captureRange
    timeRangeLabel.text = formatTimeRangeAsString(selectionRange = selectionRange, relativeZeroPoint = captureRange.min.toLong())
    durationLabel.text = TimeFormatter.getSingleUnitDurationString(selectionRange.length.toLong())

    var totalEnergyUws = 0L
    // The CpuCapture containing the system trace data is always the first element in the tab model's data series
    val cpuCapture = tabModel.dataSeries[0]
    val powerRailCounters = cpuCapture.systemTraceData?.powerRailCounters
    powerRailCounters?.forEach {
      val powerUsageRange = computePowerUsageRange(it.value.cumulativeData, selectionRange)
      totalEnergyUws += computeCumulativeEnergyInRange(powerUsageRange)
    }

    energyUsedLabel.text = "${formatLongValueWithCommas(totalEnergyUws)} $POWER_RAIL_UNIT"
  }
}