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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.TooltipModel

class PowerRailTooltip(val timeline: Timeline,
                       val counterName: String,
                       private val powerRailValues: RangedSeries<Long>) : TooltipModel, AspectModel<PowerRailTooltip.Aspect>() {
  enum class Aspect {
    /**
     * The hovering power rail value changed.
     */
    VALUE_CHANGED
  }

  // Unit of energy is microwatt-seconds (µWs)
  var activeValueUws = 0L
    private set

  override fun dispose() {
    timeline.tooltipRange.removeDependencies(this)
  }

  private fun updateValue() {
    val series = powerRailValues.getSeriesForRange(timeline.tooltipRange)
    val newValueUws = if (series.isEmpty()) 0L else series[0].value
    if (newValueUws != activeValueUws) {
      activeValueUws = newValueUws
      changed(Aspect.VALUE_CHANGED)
    }
  }

  init {
    timeline.tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE) { updateValue() }
  }
}