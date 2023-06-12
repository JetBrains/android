/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.timeline

import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.InspectorLayout
import com.android.tools.idea.compose.preview.animation.Transition

/** Curves for all properties of [Transition]. */
class TransitionCurve
private constructor(
  state: ElementState,
  private val propertyCurves: List<PropertyCurve>,
  positionProxy: PositionProxy
) : ParentTimelineElement(state, propertyCurves, positionProxy) {

  companion object {
    fun create(
      state: ElementState,
      transition: Transition,
      rowMinY: Int,
      positionProxy: PositionProxy
    ): TransitionCurve {
      var currentMinY = rowMinY
      val curves =
        transition.properties.values.filterNotNull().mapIndexed { index, it ->
          val curve = PropertyCurve.create(state, it, currentMinY, index, positionProxy)
          currentMinY += curve.heightScaled()
          curve
        }
      return TransitionCurve(state, curves, positionProxy)
    }

    fun expectedHeight(transition: Transition) =
      transition.properties.values.filterNotNull().sumOf {
        it.components.size * InspectorLayout.TIMELINE_CURVE_ROW_HEIGHT
      }
  }

  var timelineUnits: List<ComposeUnit.TimelineUnit?> = listOf()
    set(units) {
      field = units
      propertyCurves.forEachIndexed { index, it -> it.timelineUnit = units.getOrNull(index) }
    }
}
