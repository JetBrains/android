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
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.ArrowDirection
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.naveditor.scene.SELF_ACTION_RADII
import com.android.tools.idea.naveditor.scene.getSelfActionIconRect
import com.android.tools.idea.naveditor.scene.makeDrawArrowCommand
import com.android.tools.idea.naveditor.scene.selfActionPoints
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils
import com.google.common.annotations.VisibleForTesting
import java.awt.Color
import java.awt.geom.GeneralPath
import java.awt.geom.Rectangle2D


class DrawSelfAction(@VisibleForTesting @SwingCoordinate val rectangle: Rectangle2D.Float,
                     @VisibleForTesting val scale: Float,
                     @VisibleForTesting val color: Color,
                     @VisibleForTesting val isPopAction: Boolean) : CompositeDrawCommand() {

  private constructor(tokens: Array<String>)
    : this(stringToRect2D(tokens[0]), tokens[1].toFloat(), stringToColor(tokens[2]), tokens[3].toBoolean())

  constructor(serialized: String) : this(parse(serialized, 4))

  override fun serialize(): String = buildString(javaClass.simpleName, rect2DToString(rectangle),
                                                 scale, colorToString(color), isPopAction)

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()

    val points = selfActionPoints(rectangle, scale)
    val path = GeneralPath()
    points[0].let { path.moveTo(it.x, it.y) }
    DrawConnectionUtils.drawRound(path,
                                  points.map { it.x.toInt() }.toIntArray(),
                                  points.map { it.y.toInt() }.toIntArray(),
                                  points.size,
                                  SELF_ACTION_RADII.map { (it * scale).toInt() }.toIntArray())
    list.add(DrawShape(path, color, ACTION_STROKE))

    val width = NavSceneManager.ACTION_ARROW_PERPENDICULAR * scale
    val height = NavSceneManager.ACTION_ARROW_PARALLEL * scale
    val x = points[4].x - width / 2
    val y = points[4].y - height
    val drawArrow = makeDrawArrowCommand(Rectangle2D.Float(x, y, width, height), ArrowDirection.UP, color)
    list.add(drawArrow)

    if (isPopAction) {
      val iconRect = getSelfActionIconRect(points[0], scale)
      list.add(DrawIcon(iconRect, DrawIcon.IconType.POP_ACTION, color))
    }

    return list
  }
}