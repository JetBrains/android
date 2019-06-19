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
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.ArrowDirection
import com.android.tools.idea.naveditor.scene.ConnectionDirection
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.naveditor.scene.getArrowPoint
import com.android.tools.idea.naveditor.scene.getCurvePoints
import com.android.tools.idea.naveditor.scene.getRegularActionIconRect
import com.android.tools.idea.naveditor.scene.makeDrawArrowCommand
import com.google.common.annotations.VisibleForTesting
import java.awt.Color
import java.awt.geom.GeneralPath
import java.awt.geom.Rectangle2D

/**
 * [DrawCommand] that draw a nav editor action (an arrow between two screens).
 */
class DrawAction(@VisibleForTesting @SwingCoordinate val source: Rectangle2D.Float,
                 @VisibleForTesting @SwingCoordinate val dest: Rectangle2D.Float,
                 @VisibleForTesting val scale: Float,
                 @VisibleForTesting val color: Color,
                 @VisibleForTesting val isPopAction: Boolean) : CompositeDrawCommand() {

  private constructor(tokens: Array<String>)
    : this(stringToRect2D(tokens[0]), stringToRect2D(tokens[1]), tokens[2].toFloat(), stringToColor(tokens[3]), tokens[4].toBoolean())

  constructor(serialized: String) : this(parse(serialized, 5))

  override fun serialize(): String = buildString(javaClass.simpleName, rect2DToString(source), rect2DToString(dest),
                                                 scale, colorToString(color), isPopAction)

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()

    val (p1, p2, p3, p4, direction) = getCurvePoints(source, dest, scale)
    val path = GeneralPath()
    path.moveTo(p1.x, p1.y)
    path.curveTo(p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
    list.add(DrawShape(path, color, ACTION_STROKE))

    val arrowDirection = getArrowDirection(direction)
    val arrowRectangle = getArrowRectangle(direction)
    val drawArrow = makeDrawArrowCommand(arrowRectangle, arrowDirection, color)

    list.add(drawArrow)

    if (isPopAction) {
      val iconRectangle = getRegularActionIconRect(source, dest, scale)
      list.add(DrawIcon(iconRectangle, DrawIcon.IconType.POP_ACTION, color))
    }

    return list
  }

  @SwingCoordinate
  fun getArrowRectangle(direction: ConnectionDirection): Rectangle2D.Float {
    val p = getArrowPoint(scale, dest, direction)

    val rectangle = Rectangle2D.Float()
    val parallel = NavSceneManager.ACTION_ARROW_PARALLEL * scale
    val perpendicular = NavSceneManager.ACTION_ARROW_PERPENDICULAR * scale
    val deltaX = direction.deltaX.toFloat()
    val deltaY = direction.deltaY.toFloat()

    rectangle.x = p.x + (if (deltaX == 0f) -perpendicular else parallel * (deltaX - 1)) / 2
    rectangle.y = p.y + (if (deltaY == 0f) -perpendicular else parallel * (deltaY - 1)) / 2
    rectangle.width = Math.abs(deltaX * parallel) + Math.abs(deltaY * perpendicular)
    rectangle.height = Math.abs(deltaX * perpendicular) + Math.abs(deltaY * parallel)

    return rectangle
  }

  companion object {
    private fun getArrowDirection(direction: ConnectionDirection): ArrowDirection {
      when (direction) {
        ConnectionDirection.LEFT -> return ArrowDirection.RIGHT
        ConnectionDirection.RIGHT -> return ArrowDirection.LEFT
        ConnectionDirection.TOP -> return ArrowDirection.DOWN
        ConnectionDirection.BOTTOM -> return ArrowDirection.UP
      }
    }
  }
}
