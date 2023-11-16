/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.TooltipModel

class CpuFrameTooltip(val timeline: Timeline) : AspectModel<CpuFrameTooltip.Aspect>(), TooltipModel {
  enum class Aspect {
    // The hovering frame state changed
    FRAME_CHANGED
  }

  private var mySeries: DataSeries<SystemTraceFrame>? = null

  var frame: SystemTraceFrame? = null
    private set

  init {
    timeline.tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE) { updateState() }
  }

  override fun dispose() {
    timeline.tooltipRange.removeDependencies(this)
  }

  fun setFrameSeries(stateSeries: DataSeries<SystemTraceFrame>?) {
    mySeries = stateSeries
    updateState()
  }

  private fun updateState() {
    // We want "value" if we can get it, but every value along the way could be null.
    frame = mySeries?.getDataForRange(timeline.tooltipRange)?.firstOrNull()?.value

    // TODO: Investigate whether or not we can wrap changed() with a conditional that checks if the frame value actually changed.
    changed(Aspect.FRAME_CHANGED)
  }
}
