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
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTooltip
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPanel

class AndroidFrameEventTooltipView(parent: JComponent, val tooltip: AndroidFrameEventTooltip) : TooltipView(tooltip.timeline) {
  @VisibleForTesting
  val labelContainer = JPanel(TabularLayout("*").setVGap(12))

  @VisibleForTesting
  val frameNumberLabel = createTooltipLabel()

  @VisibleForTesting
  val startTimeLabel = createTooltipLabel()

  @VisibleForTesting
  val durationLabel = createTooltipLabel()

  override fun createTooltip(): JComponent {
    return labelContainer
  }

  private fun updateView() {
    val activeEvent = tooltip.activeFrameEvent
    if (activeEvent is AndroidFrameEvent.Data) {
      labelContainer.isVisible = true
      frameNumberLabel.text = "Frame number: ${activeEvent.frameNumber}"
      startTimeLabel.text = "Start time: ${
        TimeFormatter.getSemiSimplifiedClockString(activeEvent.timestampUs - timeline.dataRange.min.toLong())
      }"
      durationLabel.text = "Duration: ${TimeFormatter.getSingleUnitDurationString(activeEvent.durationUs)}"
    }
    else {
      labelContainer.isVisible = false
    }
  }

  init {
    labelContainer.apply {
      add(frameNumberLabel, TabularLayout.Constraint(0, 0))
      add(startTimeLabel, TabularLayout.Constraint(1, 0))
      add(durationLabel, TabularLayout.Constraint(2, 0))
    }
    tooltip.addDependency(this).onChange(AndroidFrameEventTooltip.Aspect.VALUE_CHANGED, this::updateView)
    updateView()
  }
}