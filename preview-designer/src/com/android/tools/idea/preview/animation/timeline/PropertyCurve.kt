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
import com.android.tools.idea.preview.animation.TooltipInfo
import java.awt.Graphics2D
import java.awt.Point

/** Curves for all components of [AnimatedProperty]. */
class PropertyCurve
private constructor(
  offsetPx: Int,
  frozenValue: Int?,
  private val property: AnimatedProperty<Double>,
  private val componentCurves: List<ComponentCurve>,
) : ParentTimelineElement(offsetPx, frozenValue, componentCurves) {

  companion object {
    fun create(
      offsetPx: Int,
      frozenValue: Int?,
      property: AnimatedProperty<Double>,
      rowMinY: Int,
      colorIndex: Int,
      positionProxy: PositionProxy,
    ): PropertyCurve {
      val curves =
        List(property.components.size) { componentId ->
            ComponentCurve.create(
              offsetPx,
              frozenValue,
              property,
              componentId,
              rowMinY + componentId * InspectorLayout.timelineCurveRowHeightScaled(),
              positionProxy,
              colorIndex,
            )
          }
          .toList()
      return PropertyCurve(offsetPx, frozenValue, property, curves)
    }
  }

  var timelineUnit: AnimationUnit.TimelineUnit? = null
    set(value) {
      field = value
      boxedLabels.forEach { it.timelineUnit = value }
    }

  private var boxedLabels: List<BoxedLabel> =
    componentCurves.mapIndexed { index, curve ->
      BoxedLabel(index, property.grouped) { curve.boxedLabelPosition }
    }

  override fun paint(g: Graphics2D) {
    componentCurves.forEach { it.paint(g) }
    boxedLabels.forEach { it.paint(g) }
  }

  override fun getTooltip(point: Point): TooltipInfo? =
    boxedLabels.firstNotNullOfOrNull { it.getTooltip(point) }
}
