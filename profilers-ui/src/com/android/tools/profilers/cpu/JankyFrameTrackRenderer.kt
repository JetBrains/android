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
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.fadedGoodFrame
import com.android.tools.adtui.common.fadedMissedDeadlineJank
import com.android.tools.adtui.common.fadedOtherJank
import com.android.tools.adtui.common.goodFrame
import com.android.tools.adtui.common.missedDeadlineJank
import com.android.tools.adtui.common.otherJank
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.trackgroup.TrackRenderer
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.JankyFrameModel
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import perfetto.protos.PerfettoTrace.FrameTimelineEvent.JankType
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.util.function.BooleanSupplier

class JankyFrameTrackRenderer(private val vsyncEnabler: BooleanSupplier): TrackRenderer<JankyFrameModel> {
  override fun render(trackModel: TrackModel<JankyFrameModel, *>) =
    StateChart(trackModel.dataModel, ::renderJankyFrame).apply {
      addRowIndexChangeListener {
        trackModel.dataModel.activeSeriesIndex = it
      }
    }.let { VsyncPanel.of(it, trackModel.dataModel.vsyncSeries, vsyncEnabler)}

  private fun renderJankyFrame(g: Graphics2D,
                               rect: Rectangle2D.Float,
                               fontMetrics: FontMetrics,
                               hovered: Boolean,
                               event: AndroidFrameTimelineEvent?) {
    if (event != null) {
      val duration = event.actualEndUs - event.expectedStartUs
      val nonJankPortion = (event.expectedEndUs - event.expectedStartUs) / duration.toFloat()
      val borderY = 1
      val borderX = 1
      val textPadding = borderX + 1

      // draw entire frame
      g.color = if (hovered) event.getActiveColor() else event.getPassiveColor()
      g.fill(rect)

      // draw non-jank portion
      val expectedFrameWidth = rect.width * nonJankPortion
      g.color = ProfilerColors.CPU_STATECHART_DEFAULT_STATE
      g.fill(Rectangle2D.Float(rect.x + borderX, rect.y + borderY,
                               expectedFrameWidth - 2 * borderX, rect.height - 2 * borderY - 1))

      // draw text
      val availableTextSpace = expectedFrameWidth - textPadding * 2
      if (availableTextSpace > 1) {
        val fullText = "Frame ${event.surfaceFrameToken}: ${duration / 1000}ms"
        val text = AdtUiUtils.shrinkToFit(fullText, fontMetrics, availableTextSpace)
        if (text.isNotEmpty()) {
          g.color = if (hovered) JBColor.DARK_GRAY else JBColor.LIGHT_GRAY
          val textOffset = rect.y + (rect.height - fontMetrics.height) * .5f + fontMetrics.ascent.toFloat()
          g.drawString(text, rect.x + textPadding, textOffset)
        }
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