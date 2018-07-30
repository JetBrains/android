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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.*
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.DRAW_ACTION_LEVEL
import com.android.tools.idea.naveditor.scene.SELF_ACTION_RADII
import com.android.tools.idea.naveditor.scene.selfActionPoints
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.GeneralPath

class DrawSelfAction(@SwingCoordinate private val start: Point,
                     @SwingCoordinate private val end: Point,
                     private val myColor: Color) : DrawCommand {
  private constructor(sp: Array<String>) : this(stringToPoint(sp[0]), stringToPoint(sp[1]), stringToColor(sp[2]))

  constructor(s: String) : this(parse(s, 3))

  override fun getLevel(): Int {
    return DRAW_ACTION_LEVEL
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val path = GeneralPath()
    path.moveTo(start.x.toFloat(), start.y.toFloat())

    val points = selfActionPoints(start, end, sceneContext)
    DrawConnectionUtils.drawRound(path, points.map { it.x }.toIntArray(), points.map { it.y }.toIntArray(), points.size,
                                  SELF_ACTION_RADII.map { sceneContext.getSwingDimension(it) }.toIntArray())

    val g2 = g.create() as Graphics2D

    g2.color = myColor
    g2.stroke = ACTION_STROKE
    g2.draw(path)

    g2.dispose()
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, pointToString(start), pointToString(end), colorToString(myColor))
  }
}