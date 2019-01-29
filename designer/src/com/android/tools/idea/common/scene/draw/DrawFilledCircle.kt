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
import com.android.tools.idea.common.scene.LerpFloat
import com.android.tools.idea.common.scene.SceneContext
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D

class DrawFilledCircle(private val level: Int,
                       @SwingCoordinate private val center: Point2D.Float,
                       private val color: Color,
                       @SwingCoordinate private val radius: LerpFloat) : DrawCommandBase() {

  constructor(myLevel: Int,
              @SwingCoordinate myCenter: Point2D.Float,
              myColor: Color,
              @SwingCoordinate radius: Float) : this(myLevel, myCenter, myColor, LerpFloat(radius))

  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint2D(sp[1]),
                                                stringToColor(sp[2]), stringToLerp(sp[3]))

  constructor(s: String) : this(parse(s, 4))

  override fun getLevel(): Int {
    return level
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName,
                       level,
                       point2DToString(center),
                       colorToString(color),
                       lerpToString(radius))
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    val r = radius.getValue(sceneContext.time)

    g.color = color
    var circle = Ellipse2D.Float(center.x - r, center.y - r, 2 * r, 2 * r)
    g.fill(circle)

    if (!radius.isComplete(sceneContext.time)) {
      sceneContext.repaint()
    }
  }
}