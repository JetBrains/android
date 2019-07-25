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
package com.android.tools.idea.common.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.google.common.annotations.VisibleForTesting
import java.awt.Color
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/**
 * [FillArrow] draws a triangular arrow in the specified rectangle.
 */

enum class ArrowDirection {
  LEFT,
  UP,
  RIGHT,
  DOWN
}

class FillArrow(@VisibleForTesting val direction: ArrowDirection,
                @VisibleForTesting @SwingCoordinate val rectangle: Rectangle2D.Float,
                color: Color,
                level: Int = 0)
  : FillShape(buildPath(rectangle, direction), color, level) {

  override fun serialize(): String = ""

  companion object {
    private fun buildPath(rectangle: Rectangle2D.Float, direction: ArrowDirection): Path2D.Float {
      val left = rectangle.x
      val right = left + rectangle.width

      val xValues = when (direction) {
        ArrowDirection.LEFT -> floatArrayOf(right, left, right)
        ArrowDirection.RIGHT -> floatArrayOf(left, right, left)
        else -> floatArrayOf(left, (left + right) / 2, right)
      }

      val top = rectangle.y
      val bottom = top + rectangle.height

      val yValues = when (direction) {
        ArrowDirection.UP -> floatArrayOf(bottom, top, bottom)
        ArrowDirection.DOWN -> floatArrayOf(top, bottom, top)
        else -> floatArrayOf(top, (top + bottom) / 2, bottom)
      }

      val path = Path2D.Float()
      path.moveTo(xValues[0], yValues[0])

      for (i in 1 until xValues.count()) {
        path.lineTo(xValues[i], yValues[i])
      }

      path.closePath()
      return path
    }
  }
}
