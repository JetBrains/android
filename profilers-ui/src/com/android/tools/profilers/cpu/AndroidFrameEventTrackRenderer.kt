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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.chart.statechart.StateChart
import com.android.tools.adtui.chart.statechart.StateChartColorProvider
import com.android.tools.adtui.chart.statechart.StateChartTextConverter
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.trackgroup.TrackRenderer
import com.android.tools.profilers.DataVisualizationColors
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTrackModel
import java.awt.Color
import javax.swing.JComponent

/**
 * Track renderer for the a frame lifecycle track representing Android frames in a specific rendering phase.
 */
class AndroidFrameEventTrackRenderer : TrackRenderer<AndroidFrameEventTrackModel> {
  override fun render(trackModel: TrackModel<AndroidFrameEventTrackModel, *>) =
    StateChart(trackModel.dataModel, AndroidFrameEventColorProvider(), AndroidFrameEventTextProvider()).apply {
      addRowIndexChangeListener {
        trackModel.dataModel.activeSeriesIndex = it
      }
    }.let { VsyncPanel.of(it, trackModel.dataModel.vsyncSeries)}
}

private class AndroidFrameEventColorProvider : StateChartColorProvider<AndroidFrameEvent>() {
  override fun getColor(isMouseOver: Boolean, value: AndroidFrameEvent): Color = when (value) {
    is AndroidFrameEvent.Data -> DataVisualizationColors.paletteManager.getBackgroundColor(value.frameNumber, isMouseOver)
    is AndroidFrameEvent.Padding -> ProfilerColors.CPU_STATECHART_DEFAULT_STATE
  }

  override fun getFontColor(isMouseOver: Boolean, value: AndroidFrameEvent): Color = when (value) {
    is AndroidFrameEvent.Data -> DataVisualizationColors.paletteManager.getForegroundColor(value.frameNumber)
    is AndroidFrameEvent.Padding -> AdtUiUtils.DEFAULT_FONT_COLOR
  }
}

private class AndroidFrameEventTextProvider : StateChartTextConverter<AndroidFrameEvent> {
  override fun convertToString(value: AndroidFrameEvent): String = when (value) {
    is AndroidFrameEvent.Data -> value.frameNumber.toString()
    is AndroidFrameEvent.Padding -> ""
  }
}