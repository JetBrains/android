/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.adtui.PaginatedTableView
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.analysis.AndroidFrameTimelineAnalysisModel.Column.APP_TIME
import com.android.tools.profilers.cpu.analysis.AndroidFrameTimelineAnalysisModel.Column.COMPOSITION_TIME
import com.android.tools.profilers.cpu.analysis.AndroidFrameTimelineAnalysisModel.Column.FRAME_NUMBER
import com.android.tools.profilers.cpu.analysis.AndroidFrameTimelineAnalysisModel.Column.GPU_TIME
import com.android.tools.profilers.cpu.analysis.AndroidFrameTimelineAnalysisModel.Column.TOTAL_TIME
import com.android.tools.profilers.cpu.analysis.TableUtils.setColumnRenderers
import java.awt.BorderLayout

class AndroidFrameTimelineTab(profilersView: StudioProfilersView, model: AndroidFrameTimelineAnalysisModel.Tab)
  : CpuAnalysisTab<AndroidFrameTimelineAnalysisModel.Tab>(profilersView, model) {
    init {
      layout = BorderLayout()
      val tableView = PaginatedTableView(model.table, PAGE_SIZE_VALUES)
      with (tableView.table) {
        showVerticalLines = true
        showHorizontalLines = true
        setColumnRenderers<AndroidFrameTimelineAnalysisModel.Column> { when (it) {
          FRAME_NUMBER -> CustomBorderTableCellRenderer()
          TOTAL_TIME, APP_TIME, GPU_TIME, COMPOSITION_TIME -> relaxedDurationRenderer()
        } }
      }
      add(tableView.component)
    }

  companion object {
    private fun relaxedDurationRenderer() = object : DurationRenderer() {
      override fun setValue(value: Any?) {
        text = when (val duration = value as Long) {
          AndroidFrameTimelineAnalysisModel.INVALID_DURATION -> "(Unavailable)"
          else -> TimeFormatter.getSingleUnitDurationString(duration)
        }
      }
    }
    private val PAGE_SIZE_VALUES = arrayOf(10, 25, 50, 100)
  }

  override fun onRemoved() {
    // this tab doesn't leak heavy listeners
  }

  override fun onReattached() {
    // this tab doesn't remove anything to restore
  }
}