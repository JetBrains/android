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

import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.PLACEHOLDER_TEXT
import com.android.tools.idea.naveditor.scene.RefinableImage
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

var IMAGE_DIMENSION = Dimension(5, 5)
var DRAW_COMMAND_RECTANGLE = Rectangle2D.Float(1f, 2f, 3f, 4f)

class TestDrawNavScreen {
  @Test
  fun testPaint() {
    var graphics = createGraphics()
    val context = mock(SceneContext::class.java)

    var drawCommand = DrawNavScreen(createRectangle(), RefinableImage())
    drawCommand.paint(graphics, context)
    verify(graphics).setColor(eq(PLACEHOLDER_BACKGROUND))
    verify(graphics).setColor(eq(PLACEHOLDER_TEXT))
    verify(graphics).drawString(eq("Preview"), anyFloat(), anyFloat())
    verify(graphics).drawString(eq("Unavailable"), anyFloat(), anyFloat())

    graphics = createGraphics()

    drawCommand = DrawNavScreen(createRectangle(), RefinableImage(null, CompletableFuture()))
    drawCommand.paint(graphics, context)
    verify(graphics).setColor(eq(PLACEHOLDER_BACKGROUND))
    verify(graphics).setColor(eq(PLACEHOLDER_TEXT))
    verify(graphics).drawString(eq("Loading..."), anyFloat(), anyFloat())

    graphics = createGraphics()

    val image = createImage()
    drawCommand = DrawNavScreen(createRectangle(), RefinableImage(null, CompletableFuture.completedFuture(RefinableImage(image))))
    drawCommand.paint(graphics, context)
    verify(graphics).drawImage(image, 1, 2, 0, 0, null)

    graphics = createGraphics()

    val oldImage = createImage()

    drawCommand = DrawNavScreen(createRectangle(), RefinableImage(oldImage, CompletableFuture.completedFuture(RefinableImage(image))))
    drawCommand.paint(graphics, context)
    verify(graphics).drawImage(image, 1, 2, 0, 0, null)

    graphics = createGraphics()

    drawCommand = DrawNavScreen(createRectangle(), RefinableImage(oldImage, CompletableFuture()))
    drawCommand.paint(graphics, context)
    verify(graphics).drawImage(oldImage, 1, 2, 0, 0, null)
  }

  private fun createGraphics(): Graphics2D {
    val graphics = mock(Graphics2D::class.java)
    `when`(graphics.create()).thenReturn(graphics)
    val metrics = mock(FontMetrics::class.java)
    `when`(graphics.fontMetrics).thenReturn(metrics)
    return graphics
  }

  private fun createRectangle() : Rectangle2D.Float {
    return Rectangle2D.Float(DRAW_COMMAND_RECTANGLE.x, DRAW_COMMAND_RECTANGLE.y,
                                  DRAW_COMMAND_RECTANGLE.width, DRAW_COMMAND_RECTANGLE.height)
  }

  private fun createImage() : BufferedImage {
    val image = mock(BufferedImage::class.java)
    `when`(image.width).thenReturn(IMAGE_DIMENSION.width)
    `when`(image.height).thenReturn(IMAGE_DIMENSION.height)
    return image
  }
}