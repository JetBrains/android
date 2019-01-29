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
import com.android.tools.idea.common.scene.SceneContext
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/**
 * [DrawArrow] draws a triangular arrow in the specified rectangle.
 */

enum class ArrowDirection {
  LEFT,
  UP,
  RIGHT,
  DOWN
}

// TODO: Integrate with DrawConnectionUtils
class DrawArrow(private val myLevel: Int, private val myDirection: ArrowDirection,
                @SwingCoordinate private val myRectangle: Rectangle2D.Float, private val myColor: Color) : DrawCommandBase() {
  private constructor(sp: Array<String>) : this(sp[0].toInt(), ArrowDirection.valueOf(sp[1]), stringToRect2D(sp[2]),
                                                stringToColor(sp[3]))

  constructor(s: String) : this(parse(s, 4))

  override fun getLevel() = myLevel

  override fun serialize(): String = buildString(javaClass.simpleName, myLevel, myDirection, rect2DToString(myRectangle),
                                                 colorToString(myColor))

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.color = myColor
    g.fill(path)
  }

  private val left
    get() = myRectangle.x

  private val right
    get() = myRectangle.x + myRectangle.width

  private val top
    get() = myRectangle.y

  private val bottom
    get() = myRectangle.y + myRectangle.height

  private val xValues: FloatArray
    get() = when (myDirection) {
      ArrowDirection.LEFT -> floatArrayOf(right, left, right)
      ArrowDirection.RIGHT -> floatArrayOf(left, right, left)
      else -> floatArrayOf(left, (left + right) / 2, right)
    }

  private val yValues: FloatArray
    get() = when (myDirection) {
      ArrowDirection.UP -> floatArrayOf(bottom, top, bottom)
      ArrowDirection.DOWN -> floatArrayOf(top, bottom, top)
      else -> floatArrayOf(top, (top + bottom) / 2, bottom)
    }

  private val path: Path2D.Float = buildPath()

  private fun buildPath(): Path2D.Float {
    val path = Path2D.Float()
    path.moveTo(xValues[0], yValues[0])

    for (i in 1 until xValues.count()) {
      path.lineTo(xValues[i], yValues[i])
    }
    path.closePath()

    return path
  }
}
