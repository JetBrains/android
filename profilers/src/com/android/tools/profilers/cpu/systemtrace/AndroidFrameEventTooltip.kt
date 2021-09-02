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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.TooltipModel

class AndroidFrameEventTooltip(
  val timeline: Timeline,
  private val model: AndroidFrameEventTrackModel
) : TooltipModel, AspectModel<AndroidFrameEventTooltip.Aspect>() {
  enum class Aspect {
    /**
     * The hovering frame event value changed.
     */
    VALUE_CHANGED,
  }

  val androidFramePhase: AndroidFramePhase = model.androidFramePhase
  var activeFrameEvent: AndroidFrameEvent = AndroidFrameEvent.Padding
    private set

  override fun dispose() {
    timeline.tooltipRange.removeDependencies(this)
    model.removeDependencies(this)
  }

  private fun updateValue() {
    val newFrameEvent = model.activeSeriesIndex.takeIf(model.series.indices::contains)?.let {
      model.series[it].getSeriesForRange(timeline.tooltipRange).firstOrNull()?.value
    } ?: AndroidFrameEvent.Padding
    if (newFrameEvent != activeFrameEvent) {
      activeFrameEvent = newFrameEvent
      changed(Aspect.VALUE_CHANGED)
    }
  }

  init {
    timeline.tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE) { updateValue() }
    model.addDependency(this).onChange(StateChartModel.Aspect.MODEL_CHANGED) { updateValue() }
  }
}