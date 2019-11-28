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

import com.android.tools.adtui.common.SwingLength
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.SwingRoundRectangle
import com.android.tools.adtui.common.SwingStroke
import com.android.tools.adtui.common.toSwingLength
import com.android.tools.adtui.common.toSwingRect
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.scaledAndroidLength
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.model.toScale
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.COMPONENT_LEVEL
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.naveditor.scene.NavColors.COMPONENT_BACKGROUND
import com.android.tools.idea.naveditor.scene.regularFont
import java.awt.Color
import java.awt.Font

// Swing defines rounded rectangle corners in terms of arc diameters instead of corner radii, so use 2x the desired radius value
private val NAVIGATION_ARC_SIZE = scaledAndroidLength(12f)

class DrawNestedGraph(private val rectangle: SwingRectangle,
                      private val scale: Scale,
                      private val frameColor: Color,
                      private val frameThickness: SwingLength,
                      private val text: String,
                      private val textColor: Color) : CompositeDrawCommand(COMPONENT_LEVEL) {

  constructor(serialized: String) : this(parse(serialized, 6))

  private constructor(tokens: Array<String>) : this(tokens[0].toSwingRect(), tokens[1].toScale(),
                                                    stringToColor(tokens[2]), tokens[3].toSwingLength(),
                                                    tokens[4], stringToColor(tokens[5]))

  override fun serialize() = buildString(javaClass.simpleName, rectangle.toString(), scale, colorToString(frameColor),
                                         frameThickness.toString(), text, colorToString(textColor))

  override fun buildCommands(): List<DrawCommand> {
    val arcSize = NAVIGATION_ARC_SIZE * scale
    val roundRectangle = SwingRoundRectangle(rectangle, arcSize, arcSize)

    val fillRectangle = FillShape(roundRectangle, COMPONENT_BACKGROUND)
    val drawRectangle = DrawShape(roundRectangle, frameColor, SwingStroke(frameThickness))

    val font = regularFont(scale, Font.BOLD)
    val drawText = DrawTruncatedText(text, rectangle, textColor, font, true)

    return listOf(fillRectangle, drawRectangle, drawText)
  }
}