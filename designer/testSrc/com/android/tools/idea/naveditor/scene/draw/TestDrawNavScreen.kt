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
import org.junit.Test
import org.mockito.Mockito.*
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture

class TestDrawNavScreen {
  @Test
  fun testPaint() {
    var graphics = createGraphics()
    val context = mock(SceneContext::class.java)

    var drawCommand = DrawNavScreen(Rectangle(1, 2, 3, 4), CompletableFuture.completedFuture(null), null)
    drawCommand.paint(graphics, context)
    verify(graphics).drawString(eq("Preview"), anyInt(), anyInt())
    verify(graphics).drawString(eq("Unavailable"), anyInt(), anyInt())

    graphics = createGraphics()

    drawCommand = DrawNavScreen(Rectangle(1, 2, 3, 4), CompletableFuture(), null)
    drawCommand.paint(graphics, context)
    verify(graphics).drawString(eq("Loading..."), anyInt(), anyInt())

    graphics = createGraphics()

    val image = mock(BufferedImage::class.java)
    drawCommand = DrawNavScreen(Rectangle(1, 2, 3, 4), CompletableFuture.completedFuture(image), null)
    drawCommand.paint(graphics, context)
    verify(graphics).drawImage(image, 1, 2, 3, 4, null)

    graphics = createGraphics()

    val oldImage = mock(BufferedImage::class.java)

    drawCommand = DrawNavScreen(Rectangle(1, 2, 3, 4), CompletableFuture.completedFuture(image), oldImage)
    drawCommand.paint(graphics, context)
    verify(graphics).drawImage(image, 1, 2, 3, 4, null)

    graphics = createGraphics()

    drawCommand = DrawNavScreen(Rectangle(1, 2, 3, 4), CompletableFuture(), oldImage)
    drawCommand.paint(graphics, context)
    verify(graphics).drawImage(oldImage, 1, 2, 3, 4, null)
  }

  private fun createGraphics(): Graphics2D {
    val graphics = mock(Graphics2D::class.java)
    `when`(graphics.create()).thenReturn(graphics)
    val metrics = mock(FontMetrics::class.java)
    `when`(graphics.fontMetrics).thenReturn(metrics)
    return graphics
  }
}