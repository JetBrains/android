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
import com.android.tools.idea.common.scene.LerpValue
import com.android.tools.idea.common.scene.SceneContext
import com.google.common.base.Joiner
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point

class DrawFilledCircle(private val myLevel: Int,
                       @SwingCoordinate private val myCenter: Point,
                       private val myColor: Color,
                       @SwingCoordinate private val myRadius: LerpValue) : DrawCommand {

  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint(sp[1]),
      stringToColor(sp[2]), stringToLerp(sp[3]))

  constructor(s: String) : this(parse(s, 4))

  override fun getLevel(): Int {
    return myLevel
  }

  override fun serialize(): String {
    return Joiner.on(',').join(javaClass.simpleName,
        myLevel,
        pointToString(myCenter),
        colorToString(myColor),
        lerpToString(myRadius))
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val r = myRadius.getValue(sceneContext.time)

    val g2 = g.create()

    g2.color = myColor
    g2.fillOval(myCenter.x - r, myCenter.y - r, 2 * r, 2 * r)

    g2.dispose()

    if (r != myRadius.end) {
      sceneContext.repaint()
    }
  }
}