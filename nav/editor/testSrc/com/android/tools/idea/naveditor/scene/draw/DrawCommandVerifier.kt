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

import com.android.tools.idea.naveditor.scene.RefinableImage
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.InOrder
import org.mockito.Mockito.times
import org.mockito.internal.verification.AtLeast
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.Stroke
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

private val PLACEHOLDER_FILL = Color(0xfdfdfd)
private val PLACEHOLDER_COLOR = Color(0xcccccc)
private val PLACEHOLDER_TEXT = Color(0xcccccc)
private val PLACEHOLDER_STROKE = BasicStroke(1f)

private val FRAME_COLOR = Color(0xa7a7a7)
private val FRAME_STROKE = BasicStroke(1f)
private val HIGHLIGHTED_STROKE = BasicStroke(2f)
private val ACTIVITY_BORDER_COLOR = Color(0xa7a7a7)
private val ACTIVITY_BORDER_STROKE = BasicStroke(1f)

private const val SPACING = 2f
private const val ARC_SIZE = 12f
private val BACKGROUND = Color(0xfafafa)

fun verifyDrawFragment(inOrder: InOrder,
                       g: Graphics2D,
                       rectangle: Rectangle2D.Float,
                       scale: Double,
                       highlightColor: Color? = null,
                       image: RefinableImage? = null) {
  verifyDrawShape(inOrder, g, rectangle, FRAME_COLOR, FRAME_STROKE)
  val imageRectangle = Rectangle2D.Float(rectangle.x + 1f, rectangle.y + 1f, rectangle.width - 2f, rectangle.height - 2f)
  verifyDrawImage(inOrder, g, imageRectangle, image)

  if (highlightColor != null) {
    val spacing = 2 * SPACING * scale.toFloat()
    val roundRectangle = RoundRectangle2D.Float(rectangle.x - spacing, rectangle.y - spacing,
                                                rectangle.width + 2 * spacing, rectangle.height + 2 * spacing,
                                                spacing, spacing)

    verifyDrawShape(inOrder, g, roundRectangle, highlightColor, HIGHLIGHTED_STROKE)
  }
}

fun verifyDrawNestedGraph(inOrder: InOrder,
                          g: Graphics2D,
                          rectangle: Rectangle2D.Float,
                          scale: Double,
                          frameColor: Color,
                          frameThickness: Float,
                          text: String,
                          textColor: Color) {
  val arcSize = ARC_SIZE * scale.toFloat()
  val roundRectangle = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arcSize, arcSize)
  verifyFillShape(inOrder, g, roundRectangle, BACKGROUND)
  verifyDrawShape(inOrder, g, roundRectangle, frameColor, BasicStroke(frameThickness))
  verifyDrawTruncatedText(inOrder, g, text, textColor)
}

fun verifyDrawActivity(inOrder: InOrder,
                       g: Graphics2D,
                       rectangle: Rectangle2D.Float,
                       imageRectangle: Rectangle2D.Float,
                       scale: Double,
                       frameColor: Color,
                       frameThickness: Float,
                       textColor: Color,
                       image: RefinableImage?) {
  val arcSize = ARC_SIZE * scale.toFloat()
  val roundRectangle = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arcSize, arcSize)
  verifyFillShape(inOrder, g, roundRectangle, BACKGROUND)
  verifyDrawShape(inOrder, g, roundRectangle, frameColor, BasicStroke(frameThickness))
  verifyDrawImage(inOrder, g, imageRectangle, image)
  verifyDrawShape(inOrder, g, imageRectangle, ACTIVITY_BORDER_COLOR, ACTIVITY_BORDER_STROKE)
  verifyDrawTruncatedText(inOrder, g, "Activity", textColor)
}

fun verifyDrawImage(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float, image: RefinableImage?) {
  if (image == null) {
    verifyDrawPlaceholder(inOrder, g, rectangle)
    return
  }

  val lastCompleted = image.lastCompleted
  val bufferedImage = lastCompleted.image

  when {
    bufferedImage != null -> verifyDrawNavScreenImage(inOrder, g, rectangle, bufferedImage)
    lastCompleted.refined == null -> verifyDrawNavScreenPreviewUnavailable(inOrder, g, rectangle)
    else -> verifyDrawNavScreenLoading(inOrder, g, rectangle)
  }
}

fun verifyDrawNavScreenImage(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float, image: BufferedImage) {
  inOrder.verify(g).create()
  inOrder.verify(g).setRenderingHints(any())
  inOrder.verify(g).clip(argThat(ShapeArgumentMatcher(rectangle)))
  inOrder.verify(g).drawImage(eq(image), eq(rectangle.x.toInt()), eq(rectangle.y.toInt()), anyInt(), anyInt(), eq(null))
  inOrder.verify(g).dispose()
}

fun verifyDrawNavScreenLoading(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float) {
  inOrder.verify(g).create()
  inOrder.verify(g).setRenderingHints(any())
  inOrder.verify(g).clip(argThat(ShapeArgumentMatcher(rectangle)))
  inOrder.verify(g).color = PLACEHOLDER_FILL
  inOrder.verify(g).fill(argThat(ShapeArgumentMatcher(rectangle)))
  inOrder.verify(g).color = PLACEHOLDER_TEXT
  inOrder.verify(g).font = any()
  inOrder.verify(g).fontMetrics
  inOrder.verify(g).drawString(eq("Loading..."), anyFloat(), anyFloat())
  inOrder.verify(g).dispose()
}

fun verifyDrawNavScreenPreviewUnavailable(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float) {
  inOrder.verify(g).create()
  inOrder.verify(g).setRenderingHints(any())
  inOrder.verify(g).clip(argThat(ShapeArgumentMatcher(rectangle)))
  inOrder.verify(g).color = PLACEHOLDER_FILL
  inOrder.verify(g).fill(argThat(ShapeArgumentMatcher(rectangle)))
  inOrder.verify(g).color = PLACEHOLDER_TEXT
  inOrder.verify(g).font = any()
  inOrder.verify(g).fontMetrics
  inOrder.verify(g).drawString(eq("Preview"), anyFloat(), anyFloat())
  inOrder.verify(g, times(2)).fontMetrics
  inOrder.verify(g).drawString(eq("Unavailable"), anyFloat(), anyFloat())
  inOrder.verify(g).dispose()
}

fun verifyDrawPlaceholder(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float) {
  verifyFillShape(inOrder, g, rectangle, PLACEHOLDER_FILL)
  val x1 = rectangle.x
  val x2 = x1 + rectangle.width
  val y1 = rectangle.y
  val y2 = y1 + rectangle.height

  verifyDrawShape(inOrder, g, Line2D.Float(x1, y1, x2, y2), PLACEHOLDER_COLOR, PLACEHOLDER_STROKE)
  verifyDrawShape(inOrder, g, Line2D.Float(x1, y2, x2, y1), PLACEHOLDER_COLOR, PLACEHOLDER_STROKE)
}

fun verifyFillShape(inOrder: InOrder, g: Graphics2D, shape: Shape, color: Color) {
  inOrder.verify(g).create()
  inOrder.verify(g).setRenderingHints(any())
  inOrder.verify(g).color = color
  inOrder.verify(g).fill(argThat(ShapeArgumentMatcher(shape)))
  inOrder.verify(g).dispose()
}

fun verifyDrawShape(inOrder: InOrder, g: Graphics2D, shape: Shape, color: Color, stroke: Stroke) {
  inOrder.verify(g).create()
  inOrder.verify(g).setRenderingHints(any())
  inOrder.verify(g).color = color
  inOrder.verify(g).stroke = stroke
  inOrder.verify(g).draw(argThat(ShapeArgumentMatcher(shape)))
  inOrder.verify(g).dispose()
}

fun verifyDrawTruncatedText(inOrder: InOrder, g: Graphics2D, text: String, color: Color) {
  inOrder.verify(g).create()
  inOrder.verify(g).getFontMetrics(any())
  inOrder.verify(g).color = color
  inOrder.verify(g).font = any()
  inOrder.verify(g).drawString(eq(text), anyFloat(), anyFloat())
  inOrder.verify(g).dispose()
}
