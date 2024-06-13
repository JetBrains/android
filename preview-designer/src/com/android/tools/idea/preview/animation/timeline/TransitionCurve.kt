/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.animation.timeline

import com.android.tools.idea.preview.animation.AnimatedProperty
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.preview.animation.Transition
import com.intellij.openapi.util.Disposer

/** Curves for all properties of [Transition]. */
class TransitionCurve
private constructor(
  offsetPx: Int,
  frozenValue: Int?,
  private val propertyCurves: List<PropertyCurve>,
) : ParentTimelineElement(offsetPx, frozenValue, propertyCurves) {

  companion object {
    fun create(
      offsetPx: Int,
      frozenValue: Int?,
      transition: Transition,
      rowMinY: Int,
      positionProxy: PositionProxy,
    ): TransitionCurve {
      var currentMinY = rowMinY
      val properties =
        transition.properties.values.ifEmpty {
          // In case there are no properties in the transition - add one "empty" property instead.
          listOf(
            AnimatedProperty.Builder()
              .add(positionProxy.minimumValue(), AnimationUnit.IntUnit(0))
              .add(positionProxy.minimumValue(), AnimationUnit.IntUnit(0))
              .build()
          )
        }
      val curves =
        properties.filterNotNull().mapIndexed { index, it ->
          val curve =
            PropertyCurve.create(offsetPx, frozenValue, it, currentMinY, index, positionProxy)
          currentMinY += curve.heightScaled()
          curve
        }
      return TransitionCurve(offsetPx, frozenValue, curves).also {
        curves.forEach { curve -> Disposer.register(it, curve) }
      }
    }

    fun expectedHeight(transition: Transition) =
      transition.properties.values.filterNotNull().sumOf {
        it.components.size * InspectorLayout.TIMELINE_CURVE_ROW_HEIGHT
      }
  }

  var timelineUnits: List<AnimationUnit.TimelineUnit?> = listOf()
    set(units) {
      field = units
      propertyCurves.forEachIndexed { index, it -> it.timelineUnit = units.getOrNull(index) }
    }
}
