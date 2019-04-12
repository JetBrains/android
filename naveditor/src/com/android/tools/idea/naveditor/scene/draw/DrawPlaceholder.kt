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

import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BORDER
import com.android.tools.idea.naveditor.scene.decorator.REGULAR_FRAME_THICKNESS
import java.awt.BasicStroke
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

data class DrawPlaceholder(private val level: Int, private val rectangle: Rectangle2D.Float) : CompositeDrawCommand() {
  private constructor(sp: Array<String>)
    : this(sp[0].toInt(), stringToRect2D(sp[1]))

  constructor(s: String) : this(parse(s, 2))

  override fun getLevel(): Int = level

  override fun serialize(): String = buildString(javaClass.simpleName, level, rect2DToString(rectangle))

  override fun buildCommands(): List<DrawCommand> {
    val rect = DrawFilledRectangle(0, rectangle, PLACEHOLDER_BACKGROUND)

    val color = PLACEHOLDER_BORDER
    val stroke = BasicStroke(REGULAR_FRAME_THICKNESS)
    val p1 = Point2D.Float(rectangle.x, rectangle.y)
    val p2 = Point2D.Float(p1.x, p1.y + rectangle.height)
    val p3 = Point2D.Float(p1.x + rectangle.width, p1.y)
    val p4 = Point2D.Float(p3.x, p2.y)

    val line1 = DrawLine(1, p1, p4, color, stroke)
    val line2 = DrawLine(2, p2, p3, color, stroke)

    return listOf(rect, line1, line2)
  }
}