/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.common.scene.draw.ArrowDirection
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawArrow
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.NavSceneManager.ACTION_ARROW_PARALLEL
import com.android.tools.idea.naveditor.scene.NavSceneManager.ACTION_ARROW_PERPENDICULAR
import java.awt.Color
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

data class DrawHorizontalAction(private val level: Int,
                                @SwingCoordinate private val rectangle: Rectangle2D.Float,
                                private val color: Color) : CompositeDrawCommand() {
  private constructor(sp: Array<String>)
    : this(sp[0].toInt(), stringToRect2D(sp[1]), stringToColor(sp[2]))

  constructor(s: String) : this(parse(s, 3))

  override fun getLevel(): Int = level

  override fun serialize(): String = buildString(javaClass.simpleName, level, rect2DToString(rectangle), colorToString(color))

  override fun buildCommands(): List<DrawCommand> {
    val arrowWidth = rectangle.height * ACTION_ARROW_PARALLEL / ACTION_ARROW_PERPENDICULAR
    val lineLength = Math.max(0f, rectangle.width - arrowWidth)

    val p1 = Point2D.Float(rectangle.x, rectangle.centerY.toFloat())
    val p2 = Point2D.Float(p1.x + lineLength, p1.y)
    val drawLine = DrawLine(0, p1, p2, color, ACTION_STROKE)

    val arrowRect = Rectangle2D.Float(p2.x, rectangle.y, arrowWidth, rectangle.height)
    val drawArrow = DrawArrow(1, ArrowDirection.RIGHT, arrowRect, color)
    return listOf(drawLine, drawArrow)
  }
}