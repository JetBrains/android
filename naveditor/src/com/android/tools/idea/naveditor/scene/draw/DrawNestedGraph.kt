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

import com.google.common.annotations.VisibleForTesting
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawFilledRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.NavColors.COMPONENT_BACKGROUND
import com.android.tools.idea.naveditor.scene.regularFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
@NavCoordinate
@VisibleForTesting
val NAVIGATION_ARC_SIZE = JBUI.scale(12f)

data class DrawNestedGraph(private val rectangle: Rectangle2D.Float,
                           private val scale: Float,
                           private val frameColor: Color,
                           private val frameThickness: Float,
                           private val text: String,
                           private val textColor: Color) : CompositeDrawCommand() {

  constructor(serialized: String) : this(parse(serialized, 6))

  private constructor(tokens: Array<String>) : this(stringToRect2D(tokens[0]), tokens[1].toFloat(),
                                                    stringToColor(tokens[2]), tokens[3].toFloat(),
                                                    tokens[4], stringToColor(tokens[5]))

  override fun getLevel() = DRAW_FRAME_LEVEL

  override fun serialize() = buildString(javaClass.simpleName, rect2DToString(rectangle), scale, colorToString(frameColor),
                                         frameThickness.toString(), text, colorToString(textColor))

  override fun buildCommands(): List<DrawCommand> {
    val arcSize = NAVIGATION_ARC_SIZE * scale
    val roundRectangle = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arcSize, arcSize)

    val fillRectangle = DrawFilledRoundRectangle(0, roundRectangle, COMPONENT_BACKGROUND)
    val drawRectangle = DrawRoundRectangle(1, roundRectangle, frameColor, frameThickness)

    val font = regularFont(scale, Font.BOLD)
    val rectangle = Rectangle2D.Float(roundRectangle.x, roundRectangle.y, roundRectangle.width, roundRectangle.height)
    val drawText = DrawTruncatedText(2, text, rectangle, textColor, font, true)

    return listOf(fillRectangle, drawRectangle, drawText)
  }
}