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
import com.android.tools.adtui.common.SwingLine
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.max
import com.android.tools.adtui.common.toSwingRect
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.times
import com.android.tools.idea.common.model.toScale
import com.android.tools.idea.common.scene.draw.DrawCommand.COMPONENT_LEVEL
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.naveditor.scene.ACTION_ARROW_PARALLEL
import com.android.tools.idea.naveditor.scene.ArrowDirection
import com.android.tools.idea.naveditor.scene.getHorizontalActionIconRect
import java.awt.Color

class DrawHorizontalAction(private val rectangle: SwingRectangle,
                           scale: Scale,
                           color: Color,
                           isPopAction: Boolean) : DrawActionBase(scale, color, isPopAction, COMPONENT_LEVEL) {
  private constructor(tokens: Array<String>)
    : this(tokens[0].toSwingRect(), tokens[1].toScale(), stringToColor(tokens[2]), tokens[3].toBoolean())

  constructor(serialized: String) : this(parse(serialized, 4))

  override fun serialize() = buildString(javaClass.simpleName, rectangle.toString(),
                                         scale, colorToString(color), isPopAction)

  override fun buildAction(): Action {
    val arrowWidth = ACTION_ARROW_PARALLEL * scale
    val lineLength = max(SwingLength(0f), rectangle.width - arrowWidth)

    val x1 = rectangle.x
    val x2 = x1 + lineLength
    val y = rectangle.center.y

    val arrowRect = SwingRectangle(x2, rectangle.y, arrowWidth, rectangle.height)
    return Action(SwingLine(x1, y, x2, y), arrowRect, ArrowDirection.RIGHT)
  }

  override fun getPopIconRectangle(): SwingRectangle = getHorizontalActionIconRect(rectangle, scale)
}