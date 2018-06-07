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
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point

/**
 * [DrawLine] draws a line between the specified endpoints.
 */
// TODO: Integrate with DisplayList.addLine
class DrawLine(private val myLevel: Int, @SwingCoordinate private val myFrom: Point, @SwingCoordinate private val myTo: Point,
               private val myColor: Color, private val myStroke: BasicStroke) : DrawCommand {

  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint(sp[1]), stringToPoint(sp[2]),
      stringToColor(sp[3]), stringToStroke(sp[4]))

  constructor(s: String) : this(parse(s, 5))

  override fun getLevel(): Int {
    return myLevel
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, myLevel,
        pointToString(myFrom), pointToString(myTo),
        colorToString(myColor), strokeToString(myStroke))
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val g2 = g.create() as Graphics2D

    g2.color = myColor
    g2.stroke = myStroke
    g2.drawLine(myFrom.x, myFrom.y, myTo.x, myTo.y)

    g2.dispose()
  }
}