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
// TODO: Integrate with DrawConnectionUtils
class DrawArrow(private val myLevel: Int, @SwingCoordinate private val myRectangle: Rectangle, private val myColor: Color) : DrawCommand {
  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToRect(sp[1]), stringToColor(sp[2]))
  constructor(s: String) : this(parse(s, 3))

  override fun getLevel() = myLevel

  override fun serialize(): String {
    return buildString(javaClass.simpleName, myLevel, rectToString(myRectangle), colorToString(myColor))
  }

  // TODO: Add support for other directions
  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    @SwingCoordinate val x = IntArray(3)
    x[0] = myRectangle.x
    x[1] = myRectangle.x + myRectangle.width
    x[2] = myRectangle.x

    @SwingCoordinate val y = IntArray(3)
    y[0] = myRectangle.y
    y[1] = myRectangle.y + myRectangle.height / 2
    y[2] = myRectangle.y + myRectangle.height

    val g2 = g.create()

    g2.color = myColor
    g2.fillPolygon(x, y, 3)

    g2.dispose()
  }
}