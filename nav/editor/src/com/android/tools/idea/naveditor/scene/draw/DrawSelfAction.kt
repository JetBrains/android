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

import com.android.tools.adtui.common.SwingPath
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.toSwingRect
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.model.toScale
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.naveditor.scene.ACTION_ARROW_PARALLEL
import com.android.tools.idea.naveditor.scene.ACTION_ARROW_PERPENDICULAR
import com.android.tools.idea.naveditor.scene.ArrowDirection
import com.android.tools.idea.naveditor.scene.SELF_ACTION_RADII
import com.android.tools.idea.naveditor.scene.getSelfActionIconRect
import com.android.tools.idea.naveditor.scene.getStartPoint
import com.android.tools.idea.naveditor.scene.selfActionPoints
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils
import java.awt.Color


class DrawSelfAction(private val rectangle: SwingRectangle,
                     scale: Scale,
                     color: Color,
                     isPopAction: Boolean) : DrawActionBase(scale, color, isPopAction) {

  private constructor(tokens: Array<String>)
    : this(tokens[0].toSwingRect(), tokens[1].toScale(), stringToColor(tokens[2]), tokens[3].toBoolean())

  constructor(serialized: String) : this(parse(serialized, 4))

  override fun serialize() = buildString(javaClass.simpleName, rectangle.toString(),
                                         scale, colorToString(color), isPopAction)

  override fun buildAction(): Action {
    val points = selfActionPoints(rectangle, scale)
    val path = SwingPath()
    path.moveTo(points[0])
    DrawConnectionUtils.drawRound(path.value,
                                  points.map { it.x.toInt() }.toIntArray(),
                                  points.map { it.y.toInt() }.toIntArray(),
                                  points.size,
                                  SELF_ACTION_RADII.map { (it * scale).toInt() }.toIntArray())

    val width = ACTION_ARROW_PERPENDICULAR * scale
    val height = ACTION_ARROW_PARALLEL * scale
    val x = points[4].x - width / 2
    val y = points[4].y - height

    return Action(path, SwingRectangle(x, y, width, height), ArrowDirection.UP)
  }

  override fun getPopIconRectangle(): SwingRectangle = getSelfActionIconRect(getStartPoint(rectangle), scale)
}