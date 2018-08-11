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
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D

class DrawCircle(private val myLevel: Int,
                 @SwingCoordinate private val myCenter: Point2D.Float,
                 private val myColor: Color,
                 private val myStroke: BasicStroke,
                 @SwingCoordinate private val myRadius: LerpFloat) : DrawCommandBase() {

  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint2D(sp[1]),
                                                stringToColor(sp[2]), BasicStroke(sp[3].toFloat()), stringToLerp(sp[4]))

  constructor(s: String) : this(parse(s, 5))

  override fun getLevel(): Int {
    return myLevel
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName,
                       myLevel,
                       point2DToString(myCenter),
                       colorToString(myColor),
                       myStroke.lineWidth.toInt(),
                       lerpToString(myRadius))
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    val r = myRadius.getValue(sceneContext.time)

    g.color = myColor
    g.stroke = myStroke
    val circle = Ellipse2D.Float(myCenter.x - r, myCenter.y - r, 2 * r, 2 * r)
    g.draw(circle)

    g.dispose()

    if (!myRadius.isComplete(sceneContext.time)) {
      sceneContext.repaint()
    }
  }
}