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
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.*
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.NavColorSet
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle

private const val text1 = "Preview"
private const val text2 = "Unavailable"
@NavCoordinate private val TEXT_PADDING = JBUI.scale(4)
@NavCoordinate private val FONT_SIZE = JBUI.scale(14)
private const val FONT_NAME = "Default"

class DrawPreviewUnavailable(@SwingCoordinate private val rectangle: Rectangle) : DrawCommand {
  private constructor(sp: Array<String>) : this(stringToRect(sp[0]))

  constructor(s: String) : this(parse(s, 1))

  override fun getLevel(): Int {
    return DRAW_FRAME_LEVEL
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val g2 = g.create() as Graphics2D

    g2.color = NavColorSet.NO_PREVIEW_BACKGROUND_COLOR
    g2.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height)

    g2.color = NavColorSet.NO_PREVIEW_TEXT_COLOR
    g2.font = Font(FONT_NAME, Font.PLAIN, sceneContext.getSwingDimension(FONT_SIZE))

    var x = rectangle.x + (rectangle.width - g2.fontMetrics.stringWidth(text1)) / 2
    val padding = sceneContext.getSwingDimension(TEXT_PADDING)
    var y = rectangle.y + (rectangle.height - padding) / 2
    g2.drawString(text1, x, y)

    x = rectangle.x + (rectangle.width - g2.fontMetrics.stringWidth(text2)) / 2
    y += g2.fontMetrics.ascent + padding
    g2.drawString(text2, x, y)

    g2.dispose()
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, rectToString(rectangle))
  }
}