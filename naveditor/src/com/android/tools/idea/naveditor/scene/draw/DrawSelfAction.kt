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
import com.android.tools.idea.common.scene.draw.ArrowDirection
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawArrow
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.point2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToPoint2D
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.ConnectionDirection
import com.android.tools.idea.naveditor.scene.SELF_ACTION_LENGTHS
import com.android.tools.idea.naveditor.scene.SELF_ACTION_RADII
import com.android.tools.idea.naveditor.scene.getArrowPoint
import com.android.tools.idea.naveditor.scene.getArrowRectangle
import com.android.tools.idea.naveditor.scene.getSelfActionIconRect
import com.android.tools.idea.naveditor.scene.getStartPoint
import com.android.tools.idea.naveditor.scene.selfActionPoints
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D


data class DrawSelfAction(@SwingCoordinate private val start: Point2D.Float,
                     @SwingCoordinate private val end: Point2D.Float,
                     private val myColor: Color) : DrawCommandBase() {
  private constructor(tokens: Array<String>) : this(stringToPoint2D(tokens[0]), stringToPoint2D(tokens[1]), stringToColor(tokens[2]))

  constructor(serialized: String) : this(parse(serialized, 3))

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    val path = GeneralPath()
    path.moveTo(start.x, start.y)

    val points = selfActionPoints(start, end, sceneContext)
    DrawConnectionUtils.drawRound(path, points.map { it.x.toInt() }.toIntArray(), points.map { it.y.toInt() }.toIntArray(), points.size,
                                  SELF_ACTION_RADII.map { sceneContext.getSwingDimensionDip(it) }.toIntArray())

    g.color = myColor
    g.stroke = ACTION_STROKE
    g.draw(path)
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, point2DToString(start), point2DToString(end), colorToString(myColor))
  }

  companion object {
    fun buildDisplayList(list: DisplayList, sceneView: SceneView, rect: Rectangle2D.Float, color: Color, isPopAction: Boolean) {
      val start = getStartPoint(rect)
      val sceneContext = SceneContext.get(sceneView)
      val arrowPoint = getArrowPoint(sceneContext, rect, ConnectionDirection.BOTTOM)
      arrowPoint.x += rect.width / 2 + sceneContext.getSwingDimension(
        SELF_ACTION_LENGTHS[0] - SELF_ACTION_LENGTHS[2])

      val arrowRectangle = getArrowRectangle(sceneView, arrowPoint, ConnectionDirection.BOTTOM)
      val end = Point2D.Float(arrowRectangle.x + arrowRectangle.width / 2, arrowRectangle.y + arrowRectangle.height - 1)

      list.add(DrawArrow(0, ArrowDirection.UP, arrowRectangle, color))
      list.add(DrawSelfAction(start, end, color))

      if (isPopAction) {
        val iconRect = getSelfActionIconRect(start, sceneContext)
        list.add(DrawIcon(iconRect, DrawIcon.IconType.POP_ACTION, color))
      }
    }
  }
}