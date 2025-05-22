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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineTooltip
import com.android.tools.profilers.cpu.systemtrace.getTitle
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import javax.swing.JComponent

class AndroidFrameTimelineTooltipView(parent: JComponent, val tooltip: AndroidFrameTimelineTooltip): TooltipView(tooltip.timeline) {
  @VisibleForTesting val container = JBPanel<Nothing>(TabularLayout("*").setVGap(12))
  @VisibleForTesting val typeLabel = JBLabel().apply { font = ProfilerFonts.H3_FONT }
  @VisibleForTesting val frameLabel = JBLabel()
  @VisibleForTesting val actualLabel = JBLabel()
  @VisibleForTesting val startLabel = JBLabel()
  @VisibleForTesting val expectedLabel = JBLabel()

  init {
    with(container) {
      add(typeLabel, TabularLayout.Constraint(0, 0))
      add(frameLabel, TabularLayout.Constraint(1, 0))
      add(actualLabel, TabularLayout.Constraint(2, 0))
      add(startLabel, TabularLayout.Constraint(3, 0))
      add(expectedLabel, TabularLayout.Constraint(4, 0))
      add(AdtUiUtils.createHorizontalSeparator(), TabularLayout.Constraint(5, 0))
      add(JBLabel("Click to inspect").apply { foreground = ProfilerColors.TOOLTIP_LOW_CONTRAST },
          TabularLayout.Constraint(6, 0))
    }
    tooltip.addDependency(this).onChange(AndroidFrameTimelineTooltip.Aspect.VALUE_CHANGED, ::updateView)
    updateView()
  }

  override fun createTooltip() = container

  private fun updateView() = when (val event = tooltip.activeEvent) {
    null -> container.isVisible = false
    else -> {
      fun offset(us: Long) = us - tooltip.model.capture.range.min.toLong()
      container.isVisible = true
      typeLabel.isVisible = event.isJank
      typeLabel.text = event.appJankType.getTitle()
      frameLabel.text = "Frame: ${event.surfaceFrameToken}"
      startLabel.text = "Start: ${TimeFormatter.getSemiSimplifiedClockString(offset(event.expectedStartUs))}"
      expectedLabel.text = "Expected end: ${TimeFormatter.getSemiSimplifiedClockString(offset(event.expectedEndUs))}"
      actualLabel.text = "Actual end: ${TimeFormatter.getSemiSimplifiedClockString(offset(event.actualEndUs))}"
    }
  }
}