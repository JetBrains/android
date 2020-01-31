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
import com.android.tools.idea.common.scene.draw.DrawCommand.COMPONENT_LEVEL
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.HEADER_ICON_SIZE
import com.android.tools.idea.naveditor.scene.HEADER_TEXT_HEIGHT
import com.android.tools.idea.naveditor.scene.HEADER_TEXT_PADDING
import com.android.tools.idea.naveditor.scene.NavColors.SUBDUED_TEXT
import com.android.tools.idea.naveditor.scene.scaledFont
import com.google.common.annotations.VisibleForTesting
import java.awt.Font
import java.awt.geom.Rectangle2D

class DrawHeader(@VisibleForTesting val rectangle: Rectangle2D.Float,
                 @VisibleForTesting val scale: Float,
                 @VisibleForTesting val text: String,
                 @VisibleForTesting val isStart: Boolean,
                 @VisibleForTesting val hasDeepLink: Boolean) : CompositeDrawCommand(COMPONENT_LEVEL) {

  constructor(serialized: String) : this(parse(serialized, 5))

  private constructor(tokens: Array<String>) : this(stringToRect2D(tokens[0]), tokens[1].toFloat(), tokens[2],
                                                    tokens[3].toBoolean(), tokens[4].toBoolean())

  override fun serialize() = buildString(javaClass.simpleName, rect2DToString(rectangle), scale, text, isStart, hasDeepLink)

  override fun buildCommands(): List<DrawCommand> {
    val list = mutableListOf<DrawCommand>()

    val textRectangle = Rectangle2D.Float()
    textRectangle.setRect(rectangle)

    val textPadding = scale * HEADER_TEXT_PADDING
    val iconSize = scale * HEADER_ICON_SIZE

    if (isStart) {
      val startRect = Rectangle2D.Float(rectangle.x, rectangle.y, iconSize, iconSize)
      list.add(DrawIcon(startRect, DrawIcon.IconType.START_DESTINATION))
      textRectangle.x += iconSize + textPadding
    }

    if (hasDeepLink) {
      val deepLinkRect = Rectangle2D.Float(rectangle.x + rectangle.width - iconSize, rectangle.y, iconSize, iconSize)
      list.add(DrawIcon(deepLinkRect, DrawIcon.IconType.DEEPLINK))
      textRectangle.width -= rectangle.height + textPadding
    }

    textRectangle.y += textPadding
    textRectangle.height = scale * HEADER_TEXT_HEIGHT

    list.add(DrawTruncatedText(0, text, textRectangle, SUBDUED_TEXT, scaledFont(scale, Font.PLAIN), false))

    return list
  }
}