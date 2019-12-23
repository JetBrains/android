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
private val IMAGE_RECTANGLE = Rectangle2D.Float(15f, 25f, 60f, 100f)
private const val SCALE = 1.5
@Suppress("UndesirableClassUsage")
private val IMAGE = BufferedImage(5, 5, TYPE_INT_ARGB)
private const val FRAME_THICKNESS = 1.5f
private val FRAME_COLOR = Color.RED
private val TEXT_COLOR = Color.BLUE
private val HIGHLIGHT_COLOR = Color.GREEN

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
    testDrawFragment(null, null)
  }

  fun testDrawHighlightedFragment() {
    testDrawFragment(HIGHLIGHT_COLOR, null)
  }

  fun testDrawFragmentWithPreviewUnavailable() {
    testDrawFragment(null, RefinableImage())
  }

  fun testDrawFragmentWithLoading() {
    testDrawFragment(null, RefinableImage(null, CompletableFuture()))
  }

  fun testDrawFragmentWithImage() {
    testDrawFragment(null, RefinableImage(IMAGE))
  }

  private fun testDrawFragment(highlightColor: Color?, image: RefinableImage?) {
    val command = DrawFragment(SwingRectangle(RECTANGLE), Scale(SCALE), highlightColor, image)
    verifyDrawCommand(command) { inOrder, g -> verifyDrawFragment(inOrder, g, RECTANGLE, SCALE, highlightColor, image) }
  }

  fun testDrawNestedGraph() {
    val text = "navigation_graph.xml"
    val command = DrawNestedGraph(SwingRectangle(RECTANGLE), Scale(SCALE),
                                  FRAME_COLOR, SwingLength(FRAME_THICKNESS),
                                  text, TEXT_COLOR)
    verifyDrawCommand(command) { inOrder, g ->
      verifyDrawNestedGraph(inOrder, g, RECTANGLE, SCALE, FRAME_COLOR, FRAME_THICKNESS, text, TEXT_COLOR)
    }
  }

  fun testDrawActivityWithPlaceholder() {
    testDrawActivity(null)
  }

  fun testDrawActivityWithPreviewUnavailable() {
    testDrawActivity(RefinableImage())
  }

  fun testDrawActivityWithLoading() {
    testDrawActivity(RefinableImage(null, CompletableFuture()))
  }

  fun testDrawActivityWithImage() {
    testDrawActivity(RefinableImage(IMAGE))
  }

  private fun testDrawActivity(image: RefinableImage?) {
    val command = DrawActivity(
      SwingRectangle(RECTANGLE), SwingRectangle(IMAGE_RECTANGLE), Scale(SCALE), FRAME_COLOR, SwingLength(FRAME_THICKNESS), TEXT_COLOR,
      image)
    verifyDrawCommand(command) { inOrder, g ->
      verifyDrawActivity(inOrder, g, RECTANGLE, IMAGE_RECTANGLE, SCALE, FRAME_COLOR, FRAME_THICKNESS, TEXT_COLOR, image)
    }
  }

  fun testDrawHeader() {
    testDrawHeader(false, false)
    testDrawHeader(false, true)
    testDrawHeader(true, false)
    testDrawHeader(true, true)
  }

  private fun testDrawHeader(isStart: Boolean, hasDeepLinks: Boolean) {
    val headerRect = Rectangle2D.Float(10f, 10f, 200f, 800f)
    val headerString = "header"
    val command = DrawHeader(SwingRectangle(headerRect), Scale(SCALE), headerString, isStart, hasDeepLinks)
    verifyDrawCommand(command) { inOrder, g ->
      verifyDrawHeader(inOrder, g, headerRect, SCALE, headerString, isStart, hasDeepLinks)
    }
  }

  fun testDrawAction() {
    val rectangle = SwingRectangle(Rectangle2D.Float())
    val color = Color.RED
    testDrawAction(DrawAction(rectangle, rectangle, Scale(SCALE), color, false), color, false)
    testDrawAction(DrawAction(rectangle, rectangle, Scale(SCALE), color, true), color, true)
  }

  fun testDrawSelfAction() {
    val rectangle = SwingRectangle(Rectangle2D.Float())
    val color = Color.RED
    testDrawAction(DrawSelfAction(rectangle, Scale(SCALE), color, false), color, false)
    testDrawAction(DrawSelfAction(rectangle, Scale(SCALE), color, true), color, true)
  }

  fun testDrawHorizontalAction() {
    testDrawHorizontalAction(true)
    testDrawHorizontalAction(false)
  }

  private fun testDrawAction(action: DrawCommand, color: Color, isPopAction: Boolean) {
    verifyDrawCommand(action) { inOrder, g ->
      verifyDrawAction(inOrder, g, color, isPopAction)
    }
  }

  private fun testDrawHorizontalAction(isPopAction: Boolean) {
    val rectangle = Rectangle2D.Float(10f, 10f, 40f, 20f)
    val color = Color.RED

    verifyDrawCommand(DrawHorizontalAction(SwingRectangle(rectangle), Scale(SCALE), color, isPopAction)) { inOrder, g ->
      verifyDrawHorizontalAction(inOrder, g, rectangle, SCALE, color, isPopAction)
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