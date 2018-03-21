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
import java.awt.Rectangle

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
                @SwingCoordinate private val myRectangle: Rectangle, private val myColor: Color) : DrawCommand {
  private constructor(sp: Array<String>) : this(sp[0].toInt(), ArrowDirection.valueOf(sp[1]), stringToRect(sp[2]), stringToColor(sp[3]))
  constructor(s: String) : this(parse(s, 4))

  override fun getLevel() = myLevel

  override fun serialize(): String = buildString(javaClass.simpleName, myLevel, myDirection, rectToString(myRectangle), colorToString(myColor))

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val g2 = g.create()

    g2.color = myColor
    g2.fillPolygon(xValues, yValues, 3)

    g2.dispose()
  }

  private val left
    get() = myRectangle.x

  private val right
    get() = myRectangle.x + myRectangle.width

  private val top
    get() = myRectangle.y

  private val bottom
    get() = myRectangle.y + myRectangle.height

  private val xValues: IntArray
    get() = when (myDirection) {
      ArrowDirection.LEFT -> intArrayOf(right, left, right)
      ArrowDirection.RIGHT -> intArrayOf(left, right, left)
      else -> intArrayOf(left, (left + right) / 2, right)
    }

  private val yValues: IntArray
    get() = when (myDirection) {
      ArrowDirection.UP -> intArrayOf(bottom, top, bottom)
      ArrowDirection.DOWN -> intArrayOf(top, bottom, top)
      else -> intArrayOf(top, (top + bottom) / 2, bottom)
    }
}
