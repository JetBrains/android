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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingX
import com.android.tools.adtui.common.SwingY
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * A representation of a curved action connecting two destinations
 * Consists of four control points for a cubic bezier and the direction
 * of the terminating arrow
 */
data class CurvePoints(val p1: SwingPoint,
                       val p2: SwingPoint,
                       val p3: SwingPoint,
                       val p4: SwingPoint,
                       val dir: ConnectionDirection) {
  fun curvePoint(t: Float) =
    SwingPoint(curveX(t), curveY(t))

  private fun curveX(t: Float) =
    SwingX(curveValue(p1.x.value, p2.x.value, p3.x.value, p4.x.value, t))

  private fun curveY(t: Float) =
    SwingY(curveValue(p1.y.value, p2.y.value, p3.y.value, p4.y.value, t))

  private fun curveValue(p1: Float, p2: Float, p3: Float, p4: Float, t: Float) =
    (1 - t).pow(3f) * p1 + 3 * (1 - t).pow(2f) * t * p2 + 3 * (1 - t) * t.pow(2f) * p3 + t.pow(3f) * p4

  /**
   * Returns a bounding rectangle for the cubic bezier
   */
  fun bounds(): SwingRectangle {
    val xRange = range({ p -> p.x.value }, { c, f -> c.curveX(f).value })
    val yRange = range({ p -> p.y.value }, { c, f -> c.curveY(f).value })

    val x = SwingX(xRange.min)
    val y = SwingY(yRange.min)
    val width = SwingLength(xRange.max - xRange.min)
    val height = SwingLength(yRange.max - yRange.min)

    return SwingRectangle(x, y, width, height)
  }

  private data class Range(val min: Float, val max: Float)

  /**
   * Calculates the range that the bezier occupies in a given direction
   */
  private fun range(value: (SwingPoint) -> Float, curveAt: (CurvePoints, Float) -> Float): Range {
    val v1 = value(p1)
    val v2 = value(p2)
    val v3 = value(p3)
    val v4 = value(p4)

    // Differentiate the curve equation with respect to t and solve for t
    // when the derivative is zero. This results in a quadratic equation
    // with coefficients a, b, and c.
    val a = -3 * v1 + 9 * v2 + -9 * v3 + 3 * v4
    val b = 6 * v1 - 12 * v2 + 6 * v3
    val c = -3 * v1 + 3 * v2

    // Initialize min and max to start and end points of curve
    var range = Range(minOf(v1, v4), maxOf(v1, v4))

    // Solve for t1 and t2 using quadratic formula
    val discriminant = b * b - 4 * a * c
    if (discriminant > 0) {
      val t1 = (-b - sqrt(discriminant)) / (2 * a)
      val t2 = (-b + sqrt(discriminant)) / (2 * a)
      // Update min and max as necessary
      range = updateRange(range, t1, curveAt)
      range = updateRange(range, t2, curveAt)
    }

    return range
  }

  private fun updateRange(range: Range, t: Float, curveAt: (CurvePoints, Float) -> Float): Range {
    if (t < 0 || t > 1) {
      return range
    }

    val v = curveAt(this, t)
    return Range(minOf(v, range.min), maxOf(v, range.max))
  }
}
