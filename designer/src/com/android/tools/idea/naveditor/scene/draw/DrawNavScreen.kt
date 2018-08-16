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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.*
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.DRAW_NAV_SCREEN_LEVEL
import com.android.tools.idea.naveditor.scene.NavColorSet
import com.android.tools.idea.naveditor.scene.setRenderingHints
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

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
class DrawNavScreen(@SwingCoordinate private val rectangle: Rectangle,
                    private val imageFuture: CompletableFuture<BufferedImage?>,
                    private val oldImage: BufferedImage? = null) : DrawCommandBase() {

  private constructor(sp: Array<String>) : this(stringToRect(sp[0]), CompletableFuture.completedFuture(null))

  constructor(s: String) : this(parse(s, 1))

  override fun getLevel(): Int {
    return DRAW_NAV_SCREEN_LEVEL
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, rectToString(rectangle))
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    setRenderingHints(g)
    g.clipRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height)
    val done = imageFuture.isDone
    val image = if (done) imageFuture.get() else oldImage
    if (image != null) {
      g.drawImage(image, rectangle.x, rectangle.y, rectangle.width, rectangle.height, null)
    }
    else if (done) {
      drawText(UNAVAILABLE_TEXT_1, UNAVAILABLE_TEXT_2, g, sceneContext)
    }
    else {
      drawText(LOADING_TEXT_1, null, g, sceneContext)
    }
    if (!done) {
      imageFuture.thenRun {
        sceneContext.repaint()
      }
    }
  }

  private fun drawText(text1: String, text2: String?, g: Graphics2D, sceneContext: SceneContext) {
    g.color = NavColorSet.NO_PREVIEW_BACKGROUND_COLOR
    g.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height)

    g.color = NavColorSet.NO_PREVIEW_TEXT_COLOR
    g.font = Font(FONT_NAME, Font.PLAIN, sceneContext.getSwingDimension(FONT_SIZE))

    var x = rectangle.x + (rectangle.width - g.fontMetrics.stringWidth(text1)) / 2
    val padding = sceneContext.getSwingDimension(TEXT_PADDING)
    var y = rectangle.y + (rectangle.height - padding) / 2
    g.drawString(text1, x, y)

    if (text2 != null) {
      x = rectangle.x + (rectangle.width - g.fontMetrics.stringWidth(text2)) / 2
      y += g.fontMetrics.ascent + padding
      g.drawString(text2, x, y)
    }
  }

}
