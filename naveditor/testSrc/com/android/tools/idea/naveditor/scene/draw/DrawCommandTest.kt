/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.RefinableImage
import org.mockito.ArgumentMatchers.any
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.util.concurrent.CompletableFuture

private val RECTANGLE = Rectangle2D.Float(10f, 20f, 80f, 120f)
private const val SCALE = 1.5
@Suppress("UndesirableClassUsage")
private val IMAGE = BufferedImage(5, 5, TYPE_INT_ARGB)
private const val FRAME_THICKNESS = 1.5f

class DrawCommandTest : NavTestCase() {
  fun testDrawPlaceholder() {
    val command = DrawPlaceholder(SwingRectangle(RECTANGLE))
    verifyDrawCommand(command) { inOrder, g -> verifyDrawPlaceholder(inOrder, g, RECTANGLE) }
  }

  fun testDrawNavScreenWithImage() {
    val image = RefinableImage(IMAGE)
    val command = DrawNavScreen(SwingRectangle(RECTANGLE), image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawNavScreenImage(inOrder, g, RECTANGLE, IMAGE) }
  }

  fun testDrawNavScreenWithLoading() {
    val image = RefinableImage(null, CompletableFuture())
    val command = DrawNavScreen(SwingRectangle(RECTANGLE), image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawNavScreenLoading(inOrder, g, RECTANGLE) }
  }

  fun testDrawNavScreenWithPreviewUnavailable() {
    val image = RefinableImage()
    val command = DrawNavScreen(SwingRectangle(RECTANGLE), image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawNavScreenPreviewUnavailable(inOrder, g, RECTANGLE) }
  }

  fun testDrawFragmentWithPlaceholder() {
    val command = DrawFragment(SwingRectangle(RECTANGLE), Scale(SCALE), null)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawFragment(inOrder, g, RECTANGLE, SCALE) }
  }

  fun testDrawHighlightedFragment() {
    val highlightColor = Color.RED
    val command = DrawFragment(SwingRectangle(RECTANGLE), Scale(SCALE), highlightColor)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawFragment(inOrder, g, RECTANGLE, SCALE, highlightColor) }
  }

  fun testDrawFragmentWithPreviewUnavailable() {
    val image = RefinableImage()
    val command = DrawFragment(SwingRectangle(RECTANGLE), Scale(SCALE), null, image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawFragment(inOrder, g, RECTANGLE, SCALE, null, image) }
  }

  fun testDrawFragmentWithLoading() {
    val image = RefinableImage(null, CompletableFuture())
    val command = DrawFragment(SwingRectangle(RECTANGLE), Scale(SCALE), null, image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawFragment(inOrder, g, RECTANGLE, SCALE, null, image) }
  }

  fun testDrawFragmentWithImage() {
    val image = RefinableImage(IMAGE)
    val command = DrawFragment(SwingRectangle(RECTANGLE), Scale(SCALE), null, image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawFragment(inOrder, g, RECTANGLE, SCALE, null, image) }
  }

  fun testDrawNestedGraph() {
    val frameColor = Color.RED
    val textColor = Color.BLUE
    val text = "navigation_graph.xml"
    val command = DrawNestedGraph(SwingRectangle(RECTANGLE), Scale(SCALE), frameColor, SwingLength(FRAME_THICKNESS), text, textColor)
    verifyDrawCommand(command) { inOrder, g ->
      verifyDrawNestedGraph(inOrder, g, RECTANGLE, SCALE, frameColor, FRAME_THICKNESS, text, textColor)
    }
  }

  private fun verifyDrawCommand(command: DrawCommand, verifier: (InOrder, Graphics2D) -> Unit) {
    val graphics = mock(Graphics2D::class.java)
    `when`(graphics.create()).thenReturn(graphics)

    val metrics = mock(FontMetrics::class.java)
    `when`(graphics.fontMetrics).thenReturn(metrics)
    `when`(graphics.getFontMetrics(any())).thenReturn(metrics)

    val inOrder = inOrder(graphics)
    val context = SceneContext.get()

    command.paint(graphics, context)
    verifier(inOrder, graphics)
    verifyNoMoreInteractions(graphics)
  }
}