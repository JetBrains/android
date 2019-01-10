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
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.COMPONENT_LEVEL
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.draw.FillShape
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.NavColors.ACTIVITY_BORDER
import com.android.tools.idea.naveditor.scene.NavColors.COMPONENT_BACKGROUND
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.createDrawImageCommand
import com.android.tools.idea.naveditor.scene.growRectangle
import com.android.tools.idea.naveditor.scene.scaledFont
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

@NavCoordinate
private val ACTIVITY_ARC_SIZE = JBUI.scale(12f)
@SwingCoordinate
private val ACTIVITY_BORDER_WIDTH = JBUI.scale(1f)
@VisibleForTesting
val ACTIVITY_BORDER_STROKE = BasicStroke(ACTIVITY_BORDER_WIDTH)

class DrawActivity(@VisibleForTesting @SwingCoordinate val rectangle: Rectangle2D.Float,
                   @VisibleForTesting @SwingCoordinate val imageRectangle: Rectangle2D.Float,
                   @VisibleForTesting val scale: Float,
                   @VisibleForTesting val frameColor: Color,
                   @VisibleForTesting val frameThickness: Float,
                   @VisibleForTesting val textColor: Color,
                   @VisibleForTesting val image: RefinableImage? = null) : CompositeDrawCommand(COMPONENT_LEVEL) {

  constructor(serialized: String) : this(parse(serialized, 6))

  private constructor(tokens: Array<String>) : this(stringToRect2D(tokens[0]), stringToRect2D(tokens[1]),
                                                    tokens[2].toFloat(), stringToColor(tokens[3]),
                                                    tokens[4].toFloat(), stringToColor(tokens[5]))

  override fun serialize() = buildString(javaClass.simpleName, rect2DToString(rectangle),
                                         rect2DToString(imageRectangle), scale,
                                         colorToString(frameColor), frameThickness, colorToString(textColor))

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()

    val arcSize = scale * ACTIVITY_ARC_SIZE
    @SwingCoordinate val roundRectangle = rectangle.let {
      RoundRectangle2D.Float(it.x, it.y, it.width, it.height, arcSize, arcSize)
    }

    list.add(FillShape(roundRectangle, COMPONENT_BACKGROUND))
    list.add(DrawShape(roundRectangle, frameColor, BasicStroke(frameThickness)))
    list.add(createDrawImageCommand(imageRectangle, image))

    val imageBorder = imageRectangle.let { Rectangle2D.Float(it.x, it.y, it.width, it.height) }
    growRectangle(imageBorder, ACTIVITY_BORDER_WIDTH, ACTIVITY_BORDER_WIDTH)
    list.add(DrawShape(imageRectangle, ACTIVITY_BORDER, ACTIVITY_BORDER_STROKE))

    val textHeight = rectangle.height - imageRectangle.height - (imageRectangle.x - rectangle.x)
    val textRectangle = Rectangle2D.Float(rectangle.x, imageRectangle.y + imageRectangle.height,
                                          rectangle.width, textHeight)
    list.add(DrawTruncatedText(4, "Activity", textRectangle, textColor,
                               scaledFont(scale, Font.BOLD), true))

    return list
  }
}