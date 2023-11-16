/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.adtui.common.SwingPath
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingShape
import com.android.tools.adtui.common.SwingStroke
import com.android.tools.adtui.common.scaledSwingLength
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawIcon
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.naveditor.scene.ArrowDirection
import icons.StudioIcons.NavEditor.Surface.POP_ACTION
import java.awt.BasicStroke
import java.awt.Color

private val ACTION_STROKE = SwingStroke(scaledSwingLength(3f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)

abstract class DrawActionBase(protected val scale: Scale,
                              protected val color: Color,
                              protected val isPopAction: Boolean,
                              level: Int = 0) : CompositeDrawCommand(level) {

  final override fun buildCommands(): List<DrawCommand> {
    val (shape, arrowRectangle, direction) = buildAction()

    val list = mutableListOf(DrawShape(shape, color, ACTION_STROKE), makeDrawArrowCommand(arrowRectangle, direction))

    if (isPopAction) {
      list.add(DrawIcon(POP_ACTION, getPopIconRectangle(), color))
    }

    return list
  }

  private fun makeDrawArrowCommand(rectangle: SwingRectangle, direction: ArrowDirection): DrawCommand {
    val left = rectangle.x
    val right = left + rectangle.width

    val xValues = when (direction) {
      ArrowDirection.LEFT -> arrayOf(right, left, right)
      ArrowDirection.RIGHT -> arrayOf(left, right, left)
      else -> arrayOf(left, left + (right - left) / 2, right)
    }

    val top = rectangle.y
    val bottom = top + rectangle.height

    val yValues = when (direction) {
      ArrowDirection.UP -> arrayOf(bottom, top, bottom)
      ArrowDirection.DOWN -> arrayOf(top, bottom, top)
      else -> arrayOf(top, top + (bottom - top) / 2, bottom)
    }

    val path = SwingPath()
    path.moveTo(xValues[0], yValues[0])

    for (i in 1 until xValues.count()) {
      path.lineTo(xValues[i], yValues[i])
    }
    path.closePath()

    return FillShape(path, color)
  }

  protected abstract fun getPopIconRectangle(): SwingRectangle
  protected abstract fun buildAction(): Action
  protected data class Action(val shape: SwingShape, val arrowRectangle: SwingRectangle, val direction: ArrowDirection)
}