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

import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingPath
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.toSwingRect
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.model.toScale
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.naveditor.scene.ACTION_ARROW_PARALLEL
import com.android.tools.idea.naveditor.scene.ACTION_ARROW_PERPENDICULAR
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.ArrowDirection
import com.android.tools.idea.naveditor.scene.ConnectionDirection
import com.android.tools.idea.naveditor.scene.getArrowPoint
import com.android.tools.idea.naveditor.scene.getCurvePoints
import com.android.tools.idea.naveditor.scene.getRegularActionIconRect
import com.android.tools.idea.naveditor.scene.makeDrawArrowCommand
import com.google.common.annotations.VisibleForTesting
import java.awt.Color

/**
 * [DrawCommand] that draw a nav editor action (an arrow between two screens).
 */
class DrawAction(@VisibleForTesting val source: SwingRectangle,
                 @VisibleForTesting val dest: SwingRectangle,
                 @VisibleForTesting val scale: Scale,
                 @VisibleForTesting val color: Color,
                 @VisibleForTesting val isPopAction: Boolean) : CompositeDrawCommand() {

  private constructor(tokens: Array<String>)
    : this(tokens[0].toSwingRect(), tokens[1].toSwingRect(), tokens[2].toScale(), stringToColor(tokens[3]), tokens[4].toBoolean())

  constructor(serialized: String) : this(parse(serialized, 5))

  override fun serialize(): String = buildString(javaClass.simpleName, source.toString(), dest.toString(),
                                                 scale, colorToString(color), isPopAction)

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()

    val (p1, p2, p3, p4, direction) = getCurvePoints(source, dest, scale)
    val path = SwingPath()
    path.moveTo(p1)
    path.curveTo(p2, p3, p4)
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

  private fun getArrowRectangle(direction: ConnectionDirection): SwingRectangle {
    val p = getArrowPoint(scale, dest, direction)

    val parallel = ACTION_ARROW_PARALLEL * scale
    val perpendicular = ACTION_ARROW_PERPENDICULAR * scale

    val x = p.x + when (direction) {
      ConnectionDirection.TOP, ConnectionDirection.BOTTOM -> -perpendicular / 2
      ConnectionDirection.LEFT -> -parallel
      else -> SwingLength(0f)
    }

    val y = p.y + when (direction) {
      ConnectionDirection.LEFT, ConnectionDirection.RIGHT -> -perpendicular / 2
      ConnectionDirection.TOP -> -parallel
      else -> SwingLength(0f)
    }

    val (width, height) = when (direction) {
      ConnectionDirection.LEFT, ConnectionDirection.RIGHT -> Pair(parallel, perpendicular)
      else -> Pair(perpendicular, parallel)
    }

    return SwingRectangle(x, y, width, height)
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
