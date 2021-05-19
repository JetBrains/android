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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerEvent
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTooltip
import com.google.common.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPanel

class SurfaceflingerTooltipView(parent: JComponent, val tooltip: SurfaceflingerTooltip) : TooltipView(tooltip.timeline) {
  private val content = JPanel(TabularLayout("*").setVGap(12))

  @VisibleForTesting
  val eventNameLabel = createTooltipLabel()

  @VisibleForTesting
  val durationLabel = createTooltipLabel()

  override fun createTooltip(): JComponent {
    return content
  }

  private fun timeChanged() {
    val activeEvent = tooltip.activeSurfaceflingerEvent
    if (activeEvent == null) {
      content.isVisible = false
      return
    }
    content.isVisible = true

    eventNameLabel.text = activeEvent.name
    durationLabel.text = TimeFormatter.getSingleUnitDurationString(activeEvent.end - activeEvent.start)
    val isProcessing = activeEvent.type == SurfaceflingerEvent.Type.PROCESSING
    eventNameLabel.isVisible = isProcessing
    durationLabel.isVisible = isProcessing
  }

  init {
    content.apply {
      add(eventNameLabel, TabularLayout.Constraint(0, 0))
      add(durationLabel, TabularLayout.Constraint(1, 0))
    }
    tooltip.addDependency(this).onChange(SurfaceflingerTooltip.Aspect.EVENT_CHANGED, this::timeChanged)
    timeChanged()
  }
}