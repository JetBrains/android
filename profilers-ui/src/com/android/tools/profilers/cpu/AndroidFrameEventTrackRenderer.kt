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

import com.android.tools.adtui.chart.statechart.Renderer
import com.android.tools.adtui.chart.statechart.StateChart
import com.android.tools.adtui.chart.statechart.StateChartColorProvider
import com.android.tools.adtui.chart.statechart.StateChartTextConverter
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.trackgroup.TrackRenderer
import com.android.tools.profilers.DataVisualizationColors
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.cpu.FrameTimelineSelectionOverlayPanel.GrayOutMode
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTrackModel
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.util.function.BooleanSupplier
import javax.swing.JComponent

/**
 * Track renderer for the a frame lifecycle track representing Android frames in a specific rendering phase.
 */
class AndroidFrameEventTrackRenderer(private val vsyncEnabler: BooleanSupplier) : TrackRenderer<AndroidFrameEventTrackModel> {
  override fun render(trackModel: TrackModel<AndroidFrameEventTrackModel, *>): JComponent {
    val model = trackModel.dataModel
    val isSharedTimeline = model.timelineEventByFrameNumber.isNotEmpty()
    val stateChart = when {
      isSharedTimeline ->
        StateChart(model, rendererForSharedTimeline(model.multiSelectionModel, model.timelineEventByFrameNumber))
      else -> StateChart(model, AndroidFrameEventColorProvider(), AndroidFrameEventTextProvider())
    }
    stateChart.addRowIndexChangeListener { model.activeSeriesIndex = it }
    val content = when {
      isSharedTimeline -> FrameTimelineSelectionOverlayPanel.of(stateChart, model.viewRange, model.multiSelectionModel,
                                                                GrayOutMode.NONE, true)
      else -> stateChart
    }
    return VsyncPanel.of(content, model.vsyncSeries, vsyncEnabler)
  }

  private fun rendererForSharedTimeline(multiSelectionModel: MultiSelectionModel<CpuAnalyzable<*>>,
                                        timelineEventIndex: Map<Long, AndroidFrameTimelineEvent>)
    : Renderer<AndroidFrameEvent> = { g, boundary, fontMetrics, hovered, frame ->
    if (frame is AndroidFrameEvent.Data) {
      val correspondingTimelineEvent = timelineEventIndex[frame.frameNumber.toLong()]
      val isActive = correspondingTimelineEvent === multiSelectionModel.activeSelectionKey
      // paint frame
      val borderColor = when {
        correspondingTimelineEvent == null -> JBColor.LIGHT_GRAY
        isActive -> correspondingTimelineEvent.getActiveColor()
        else -> correspondingTimelineEvent.getPassiveColor()
      }
      val borderX = 1
      val borderY = 1
      g.color = borderColor
      g.fill(boundary)
      g.color = ProfilerColors.CPU_STATECHART_DEFAULT_STATE
      g.fill(Rectangle2D.Float(boundary.x + borderX, boundary.y + borderY,
                               boundary.width - 2 * borderX, boundary.height - 2 * borderY - 1))

      // draw text
      val textPadding = borderX + 1
      val availableTextSpace = boundary.width - 2 * textPadding
      if (availableTextSpace > 1) {
        val fullText = "${frame.frameNumber}: ${TimeFormatter.getSingleUnitDurationString(frame.durationUs)}"
        val text = AdtUiUtils.shrinkToFit(fullText, fontMetrics, availableTextSpace)
        if (text.isNotEmpty()) {
          g.color = if (hovered || isActive) JBColor.DARK_GRAY else JBColor.LIGHT_GRAY
          val textOffset = boundary.y + (boundary.height - fontMetrics.height) * .5f + fontMetrics.ascent.toFloat()
          g.drawString(text, boundary.x + textPadding, textOffset)
        }
      }
    }
  }
}

private class AndroidFrameEventColorProvider : StateChartColorProvider<AndroidFrameEvent>() {
  override fun getColor(isMouseOver: Boolean, value: AndroidFrameEvent): Color = when (value) {
    is AndroidFrameEvent.Data -> DataVisualizationColors.paletteManager.getBackgroundColor(value.frameNumber, isMouseOver)
    is AndroidFrameEvent.Padding -> UIUtil.TRANSPARENT_COLOR
  }

  override fun getFontColor(isMouseOver: Boolean, value: AndroidFrameEvent): Color = when (value) {
    is AndroidFrameEvent.Data -> DataVisualizationColors.paletteManager.getForegroundColor(value.frameNumber)
    is AndroidFrameEvent.Padding -> UIUtil.TRANSPARENT_COLOR
  }
}

private class AndroidFrameEventTextProvider : StateChartTextConverter<AndroidFrameEvent> {
  override fun convertToString(value: AndroidFrameEvent): String = when (value) {
    is AndroidFrameEvent.Data -> value.frameNumber.toString()
    is AndroidFrameEvent.Padding -> ""
  }
}