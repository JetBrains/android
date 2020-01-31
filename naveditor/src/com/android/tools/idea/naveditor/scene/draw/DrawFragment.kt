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
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorOrNullToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColorOrNull
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.FRAGMENT_BORDER_SPACING
import com.android.tools.idea.naveditor.scene.NavColors
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.createDrawImageCommand
import com.android.tools.idea.naveditor.scene.decorator.HIGHLIGHTED_FRAME_STROKE
import com.android.tools.idea.naveditor.scene.decorator.REGULAR_FRAME_STROKE
import com.android.tools.idea.naveditor.scene.growRectangle
import com.google.common.annotations.VisibleForTesting
import java.awt.Color
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

class DrawFragment(@VisibleForTesting @SwingCoordinate val rectangle: Rectangle2D.Float,
                   @VisibleForTesting val scale: Float,
                   @VisibleForTesting val highlightColor: Color?,
                   @VisibleForTesting val image: RefinableImage? = null) : CompositeDrawCommand(COMPONENT_LEVEL) {

  constructor(serialized: String) : this(parse(serialized, 3))

  private constructor(tokens: Array<String>) : this(stringToRect2D(tokens[0]), tokens[1].toFloat(), stringToColorOrNull(tokens[2]))

  override fun serialize() = buildString(javaClass.simpleName, rect2DToString(rectangle), scale, colorOrNullToString(highlightColor))

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()
    list.add(DrawShape(rectangle, NavColors.FRAME, REGULAR_FRAME_STROKE))

    @SwingCoordinate val imageRectangle = Rectangle2D.Float()
    imageRectangle.setRect(rectangle)
    growRectangle(imageRectangle, -1f, -1f)

    list.add(createDrawImageCommand(imageRectangle, image))

    if (highlightColor != null) {
      @SwingCoordinate val spacing = FRAGMENT_BORDER_SPACING * scale
      @SwingCoordinate val roundRectangle = RoundRectangle2D.Float(
        rectangle.x, rectangle.y, rectangle.width, rectangle.height, 2 * spacing, 2 * spacing)

      growRectangle(roundRectangle, 2 * spacing, 2 * spacing)

      list.add(DrawShape(roundRectangle, highlightColor, HIGHLIGHTED_FRAME_STROKE))
    }

    return list
  }
}