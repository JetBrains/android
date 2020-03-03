/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.adtui.common.toSwingRect
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_TEXT
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.AffineTransform

private const val UNAVAILABLE_TEXT_1 = "Preview"
private const val UNAVAILABLE_TEXT_2 = "Unavailable"
private const val LOADING_TEXT_1 = "Loading..."
@NavCoordinate
private val TEXT_PADDING = JBUI.scale(4)
@NavCoordinate
private val FONT_SIZE = JBUI.scale(14)
private const val FONT_NAME = "Default"

/**
 * [DrawCommand] that draws a screen in the navigation editor.
 */
class DrawNavScreen(private val rectangle: SwingRectangle,
                    private val image: RefinableImage) : DrawCommandBase() {

  private constructor(tokens: Array<String>) : this(tokens[0].toSwingRect(), RefinableImage())

  constructor(serialized: String) : this(parse(serialized, 1))

  override fun serialize() = buildString(javaClass.simpleName, rectangle.toString())

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.setRenderingHints(HQ_RENDERING_HINTS)
    g.clip(rectangle.value)
    val lastCompleted = image.lastCompleted
    val image = lastCompleted.image
    if (image != null) {
      val transform = AffineTransform()
      transform.translate(rectangle.x.toDouble(), rectangle.y.toDouble())
      UIUtil.drawImage(g, image, rectangle.x.toInt(), rectangle.y.toInt(), null)
    }
    else if (lastCompleted.refined == null) {
      drawText(UNAVAILABLE_TEXT_1, UNAVAILABLE_TEXT_2, g, sceneContext)
    }
    else {
      drawText(LOADING_TEXT_1, null, g, sceneContext)
    }
    if (lastCompleted.refined != null) {
      lastCompleted.refined.thenRun {
        sceneContext.repaint()
      }
    }
  }

  private fun drawText(text1: String, text2: String?, g: Graphics2D, sceneContext: SceneContext) {
    g.color = PLACEHOLDER_BACKGROUND
    val textRectangle = rectangle.value
    g.fill(textRectangle)

    g.color = PLACEHOLDER_TEXT
    g.font = Font(FONT_NAME, Font.PLAIN, sceneContext.getSwingDimension(FONT_SIZE))

    var x = textRectangle.x + (textRectangle.width - g.fontMetrics.stringWidth(text1)) / 2
    val padding = sceneContext.getSwingDimension(TEXT_PADDING)
    var y = textRectangle.y + (textRectangle.height - padding) / 2
    g.drawString(text1, x, y)

    if (text2 != null) {
      x = textRectangle.x + (textRectangle.width - g.fontMetrics.stringWidth(text2)) / 2
      y += g.fontMetrics.ascent + padding
      g.drawString(text2, x, y)
    }
  }

}
