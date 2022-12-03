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
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.fadedGoodFrame
import com.android.tools.adtui.common.fadedMissedDeadlineJank
import com.android.tools.adtui.common.fadedOtherJank
import com.android.tools.adtui.common.goodFrame
import com.android.tools.adtui.common.missedDeadlineJank
import com.android.tools.adtui.common.otherJank
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.trackgroup.TrackRenderer
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.FrameTimelineSelectionOverlayPanel.GrayOutMode
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import com.android.tools.profilers.cpu.analysis.JankAnalysisModel
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineModel
import com.intellij.util.ui.JBUI
import perfetto.protos.PerfettoTrace.FrameTimelineEvent.JankType
import java.awt.geom.Rectangle2D
import java.util.function.BooleanSupplier
import kotlin.math.min

class JankyFrameTrackRenderer(private val profilersView: StudioProfilersView,
                              private val vsyncEnabler: BooleanSupplier): TrackRenderer<AndroidFrameTimelineModel> {
  override fun render(trackModel: TrackModel<AndroidFrameTimelineModel, *>) =
    StateChart(trackModel.dataModel, renderJankyFrame(trackModel.dataModel.multiSelectionModel)).apply {
      addRowIndexChangeListener {
        trackModel.dataModel.activeSeriesIndex = it
      }
      addItemClickedListener {
        val runInBackground = profilersView.studioProfilers.ideServices.poolExecutor::execute
        trackModel.dataModel.multiSelectionModel
          .setSelection(it, setOf(JankAnalysisModel(it, trackModel.dataModel.capture, runInBackground)))
      }
    }.let { VsyncPanel.of(FrameTimelineSelectionOverlayPanel.of(it, trackModel.dataModel.viewRange,
                                                                trackModel.dataModel.multiSelectionModel,
                                                                GrayOutMode.None, true),
                          trackModel.dataModel.vsyncSeries, vsyncEnabler)}

  private fun renderJankyFrame(multiSelectionModel: MultiSelectionModel<CpuAnalyzable<*>>): Renderer<AndroidFrameTimelineEvent> =
    { g, rect, fontMetrics, hovered, event ->
      val borderY = 1
      val borderX = 1
      val textPadding = borderX + 1
      val active = hovered || multiSelectionModel.activeSelectionKey === event
      val duration = event.actualDurationUs
      val blankRectWidth = when {
        // Only fill the after-deadline portion for "deadline missed" jank type
        event.isActionableJank -> rect.width * min(event.expectedDurationUs / duration.toFloat(), 1f)
        else -> rect.width
      }

      // draw entire frame
      g.color = if (active) event.getActiveColor() else event.getPassiveColor()
      g.fill(rect)

      // draw non-jank portion
      g.color = ProfilerColors.CPU_STATECHART_DEFAULT_STATE
      g.fill(Rectangle2D.Float(rect.x + borderX, rect.y + borderY,
                               blankRectWidth - 2 * borderX, rect.height - 2 * borderY - 1))

      // draw text
      val availableTextSpace = blankRectWidth - textPadding * 2
      if (availableTextSpace > 1) {
        val fullText = "${event.surfaceFrameToken}: ${TimeFormatter.getSingleUnitDurationString(duration)}"
        val text = AdtUiUtils.shrinkToFit(fullText, fontMetrics, availableTextSpace)
        if (text.isNotEmpty()) {
          g.color = if (active) JBUI.CurrentTheme.Label.foreground() else JBUI.CurrentTheme.Label.disabledForeground()
          val textOffset = rect.y + (rect.height - fontMetrics.height) * .5f + fontMetrics.ascent.toFloat()
          g.drawString(text, rect.x + textPadding, textOffset)
        }
      }
    }
}

fun AndroidFrameTimelineEvent.getActiveColor() = when (appJankType) {
  JankType.JANK_APP_DEADLINE_MISSED -> missedDeadlineJank
  JankType.JANK_NONE -> goodFrame
  else -> otherJank
}

fun AndroidFrameTimelineEvent.getPassiveColor() = when (appJankType) {
  JankType.JANK_APP_DEADLINE_MISSED -> fadedMissedDeadlineJank
  JankType.JANK_NONE -> fadedGoodFrame
  else -> fadedOtherJank
}