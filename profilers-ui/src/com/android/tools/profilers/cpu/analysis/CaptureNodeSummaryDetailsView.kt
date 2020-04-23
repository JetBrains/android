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

import com.android.tools.adtui.model.formatter.TimeFormatter
import com.google.common.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JLabel

class CaptureNodeSummaryDetailsView(
  parentView: JComponent, tabModel: CaptureNodeAnalysisSummaryTabModel) : SummaryDetailsViewBase<CaptureNodeAnalysisSummaryTabModel>(
  parentView, tabModel) {
  @get:VisibleForTesting
  val timeRangeLabel = JLabel()

  @get:VisibleForTesting
  val durationLabel = JLabel()

  @get:VisibleForTesting
  val dataTypeLabel = JLabel()

  init {
    val range = tabModel.selectionRange
    timeRangeLabel.text = formatTimeRangeAsString(range)
    durationLabel.text = TimeFormatter.getSingleUnitDurationString(range.length.toLong())
    dataTypeLabel.text = tabModel.label

    addRowToCommonSection("Time Range", timeRangeLabel)
    addRowToCommonSection("Duration", durationLabel)
    addRowToCommonSection("Data Type", dataTypeLabel)
  }
}