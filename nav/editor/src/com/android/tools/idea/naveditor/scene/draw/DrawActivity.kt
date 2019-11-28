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
import com.android.tools.adtui.common.scaledSwingLength
import com.android.tools.adtui.common.toSwingLength
import com.android.tools.adtui.common.toSwingRect
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.scaledAndroidLength
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
import com.android.tools.idea.naveditor.scene.NavColors.ACTIVITY_BORDER
import com.android.tools.idea.naveditor.scene.NavColors.COMPONENT_BACKGROUND
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.createDrawImageCommand
import com.android.tools.idea.naveditor.scene.scaledFont
import com.google.common.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Font

private val ACTIVITY_ARC_SIZE = scaledAndroidLength(12f)
private val ACTIVITY_BORDER_WIDTH = scaledSwingLength(1f)
@VisibleForTesting
val ACTIVITY_BORDER_STROKE = SwingStroke(ACTIVITY_BORDER_WIDTH)

class DrawActivity(@VisibleForTesting val rectangle: SwingRectangle,
                   @VisibleForTesting val imageRectangle: SwingRectangle,
                   @VisibleForTesting val scale: Scale,
                   @VisibleForTesting val frameColor: Color,
                   @VisibleForTesting val frameThickness: SwingLength,
                   @VisibleForTesting val textColor: Color,
                   @VisibleForTesting val image: RefinableImage? = null) : CompositeDrawCommand(COMPONENT_LEVEL) {

  constructor(serialized: String) : this(parse(serialized, 6))

  private constructor(tokens: Array<String>) : this(tokens[0].toSwingRect(), tokens[1].toSwingRect(),
                                                    tokens[2].toScale(), stringToColor(tokens[3]),
                                                    tokens[4].toSwingLength(), stringToColor(tokens[5]))

  override fun serialize() = buildString(javaClass.simpleName, rectangle.toString(),
                                         imageRectangle.toString(), scale,
                                         colorToString(frameColor), frameThickness, colorToString(textColor))

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()

    val arcSize = scale * ACTIVITY_ARC_SIZE
    val roundRectangle = SwingRoundRectangle(rectangle, arcSize, arcSize)

    list.add(FillShape(roundRectangle, COMPONENT_BACKGROUND))
    list.add(DrawShape(roundRectangle, frameColor, SwingStroke(frameThickness)))
    list.add(createDrawImageCommand(imageRectangle, image))

    list.add(DrawShape(imageRectangle, ACTIVITY_BORDER, ACTIVITY_BORDER_STROKE))

    val textHeight = rectangle.height - imageRectangle.height - (imageRectangle.x - rectangle.x)
    val textRectangle = SwingRectangle(rectangle.x, imageRectangle.y + imageRectangle.height,
                                       rectangle.width, textHeight)
    list.add(DrawTruncatedText("Activity", textRectangle, textColor,
                               scaledFont(scale, Font.BOLD), true))

    return list
  }
}