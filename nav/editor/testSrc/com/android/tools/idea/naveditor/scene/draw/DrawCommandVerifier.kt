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

import com.android.testutils.MockitoKt.whenever
import com.intellij.ui.JreHiDpiUtil
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.awt.BasicStroke
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Shape
import java.awt.Stroke
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

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
private val HEADER_TEXT = Color(0x656565)

private const val HEADER_ICON_SIZE = 14f
private const val HEADER_TEXT_PADDING = 2f

private val ACTION_STROKE = BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
private val ACTION_HANDLE_STROKE = BasicStroke(2f)
private val ACTION_HANDLE_OUTER_COLOR = Color(0xf5f5f5)
private val ACTION_HANDLE_INNER_COLOR = Color(0x1886f7)

private const val ACTION_ARROW_PARALLEL = 10f

private val LINE_TO_MOUSE_COLOR = Color(0x1886f7)
private val LINE_TO_MOUSE_STROKE = BasicStroke(3f)

private val EMPTY_DESIGNER_TEXT_COLOR = Color(0xa7a7a7)

enum class PreviewType {
  PLACEHOLDER,
  LOADING,
  UNAVAILABLE,
  IMAGE
}

fun verifyDrawFragment(inOrder: InOrder,
                       g: Graphics2D,
                       rectangle: Rectangle2D.Float,
                       scale: Double,
                       highlightColor: Color? = null,
                       previewType: PreviewType = PreviewType.PLACEHOLDER) {
  verifyDrawShape(inOrder, g, rectangle, FRAME_COLOR, FRAME_STROKE)
  val imageRectangle = Rectangle2D.Float(rectangle.x + 1f, rectangle.y + 1f, rectangle.width - 2f, rectangle.height - 2f)
  verifyDrawNavScreen(inOrder, g, imageRectangle, previewType)

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
                       previewType: PreviewType = PreviewType.PLACEHOLDER) {
  val arcSize = ARC_SIZE * scale.toFloat()
  val roundRectangle = RoundRectangle2D.Float(rectangle.x, rectangle.y, rectangle.width, rectangle.height, arcSize, arcSize)
  verifyFillShape(inOrder, g, roundRectangle, BACKGROUND)
  verifyDrawShape(inOrder, g, roundRectangle, frameColor, BasicStroke(frameThickness))
  verifyDrawNavScreen(inOrder, g, imageRectangle, previewType)
  verifyDrawShape(inOrder, g, imageRectangle, ACTIVITY_BORDER_COLOR, ACTIVITY_BORDER_STROKE)
  verifyDrawTruncatedText(inOrder, g, "Activity", textColor)
}

fun verifyDrawHeader(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float,
                     scale: Double, text: String, isStart: Boolean = false, hasDeepLink: Boolean = false) {

  var textX = rectangle.x
  var textWidth = rectangle.width
  val textPadding = scale.toFloat() * HEADER_TEXT_PADDING
  val iconSize = scale.toFloat() * HEADER_ICON_SIZE

  if (isStart) {
    val startRect = Rectangle2D.Float(rectangle.x, rectangle.y, iconSize, iconSize)
    verifyDrawIcon(inOrder, g, startRect)
    textX += iconSize + textPadding
    textWidth -= iconSize + textPadding
  }

  if (hasDeepLink) {
    val deepLinkRect = Rectangle2D.Float(rectangle.x + rectangle.width - iconSize, rectangle.y, iconSize, iconSize)
    verifyDrawIcon(inOrder, g, deepLinkRect)
    textWidth -= iconSize + textPadding
  }

  verifyDrawTruncatedText(inOrder, g, text, HEADER_TEXT)
}

fun verifyDrawNavScreen(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float, previewType: PreviewType) {
  when (previewType) {
    PreviewType.PLACEHOLDER -> verifyDrawPlaceholder(inOrder, g, rectangle)
    PreviewType.LOADING -> verifyDrawNavScreenLoading(inOrder, g, rectangle)
    PreviewType.UNAVAILABLE -> verifyDrawNavScreenPreviewUnavailable(inOrder, g, rectangle)
    PreviewType.IMAGE -> verifyDrawNavScreenImage(inOrder, g, rectangle)
  }
}

fun verifyDrawNavScreenImage(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float) {
  inOrder.apply {
    verify(g).setRenderingHints(any())
    verify(g).clip(argThat(ShapeArgumentMatcher(rectangle)))
    verify(g).drawImage(any(), eq(rectangle.x.toInt()), eq(rectangle.y.toInt()), anyInt(), anyInt(), eq(null))
    verify(g).dispose()
  }
}

fun verifyDrawNavScreenLoading(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float) {
  inOrder.apply {
    verify(g).setRenderingHints(any())
    verify(g).clip(argThat(ShapeArgumentMatcher(rectangle)))
    verify(g).color = PLACEHOLDER_FILL
    verify(g).fill(argThat(ShapeArgumentMatcher(rectangle)))
    verify(g).color = PLACEHOLDER_TEXT
    verify(g).font = any()
    verify(g).fontMetrics
    verify(g).drawString(eq("Loading..."), anyFloat(), anyFloat())
    verify(g).dispose()
  }
}

fun verifyDrawNavScreenPreviewUnavailable(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float) {
  inOrder.apply {
    verify(g).setRenderingHints(any())
    verify(g).clip(argThat(ShapeArgumentMatcher(rectangle)))
    verify(g).color = PLACEHOLDER_FILL
    verify(g).fill(argThat(ShapeArgumentMatcher(rectangle)))
    verify(g).color = PLACEHOLDER_TEXT
    verify(g).font = any()
    verify(g).fontMetrics
    verify(g).drawString(eq("Preview"), anyFloat(), anyFloat())
    verify(g, times(2)).fontMetrics
    verify(g).drawString(eq("Unavailable"), anyFloat(), anyFloat())
    verify(g).dispose()
  }
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
  inOrder.apply {
    verify(g).setRenderingHints(any())
    verify(g).color = color
    verify(g).fill(argThat(ShapeArgumentMatcher(shape)))
    verify(g).dispose()
  }
}

fun verifyDrawShape(inOrder: InOrder, g: Graphics2D, shape: Shape, color: Color, stroke: Stroke) {
  inOrder.apply {
    verify(g).setRenderingHints(any())
    verify(g).color = color
    verify(g).stroke = argThat(StrokeArgumentMatcher(stroke))
    verify(g).draw(argThat(ShapeArgumentMatcher(shape)))
    verify(g).dispose()
  }
}

fun verifyDrawTruncatedText(inOrder: InOrder, g: Graphics2D, text: String, color: Color) {
  inOrder.apply {
    verify(g).getFontMetrics(any())
    verify(g).color = color
    verify(g).font = any()
    verify(g).drawString(eq(text), anyFloat(), anyFloat())
    verify(g).dispose()
  }
}

fun verifyDrawAction(inOrder: InOrder, g: Graphics2D, color: Color, isPopAction: Boolean = false) {
  verifyDrawShape(inOrder, g, Path2D.Float(), color, ACTION_STROKE)
  verifyFillShape(inOrder, g, Path2D.Float(), color)
  if (isPopAction) {
    verifyDrawIcon(inOrder, g)
  }
}

fun verifyDrawHorizontalAction(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float,
                               scale: Double, color: Color, isPopAction: Boolean = false) {
  val x1 = rectangle.x
  val x2 = x1 + rectangle.width - ACTION_ARROW_PARALLEL * scale.toFloat()
  val y = rectangle.centerY.toFloat()

  verifyDrawShape(inOrder, g, Line2D.Float(x1, y, x2, y), color, ACTION_STROKE)
  verifyFillShape(inOrder, g, Path2D.Float(), color)
  if (isPopAction) {
    verifyDrawIcon(inOrder, g)
  }
}

fun verifyDrawIcon(inOrder: InOrder, g: Graphics2D, rectangle: Rectangle2D.Float? = null) {
  inOrder.apply {
    verify(g).setRenderingHints(any())
    if (rectangle == null) {
      verify(g).drawImage(any(), anyInt(), anyInt(), anyInt(), anyInt(), eq(null))
    }
    else {
      verify(g).drawImage(any(), eq(rectangle.x.toInt()), eq(rectangle.y.toInt()),
                          eq(rectangle.width.toInt()), eq(rectangle.height.toInt()), eq(null))
    }
    verify(g).dispose()
  }
}


fun verifyDrawLineToMouse(inOrder: InOrder, g: Graphics2D, center: Point2D.Float, mouseX: Int, mouseY: Int) {
  inOrder.apply {
    verify(g).color = LINE_TO_MOUSE_COLOR
    verify(g).stroke = argThat(StrokeArgumentMatcher(LINE_TO_MOUSE_STROKE))
    val line = Line2D.Float(center.x, center.y, mouseX.toFloat(), mouseY.toFloat())
    verify(g).draw(argThat(ShapeArgumentMatcher(line)))
    verify(g).dispose()
  }
}

fun verifyDrawActionHandle(inOrder: InOrder, g: Graphics2D, center: Point2D.Float, outerRadius: Float, innerRadius: Float,
                           outerColor: Color, innerColor: Color) {
  val outerEllipse = makeCircle(center, outerRadius)
  verifyFillShape(inOrder, g, outerEllipse, outerColor)

  val innerEllipse = makeCircle(center, innerRadius)
  verifyDrawShape(inOrder, g, innerEllipse, innerColor, ACTION_HANDLE_STROKE)
}

fun verifyDrawActionHandleDrag(inOrder: InOrder, g: Graphics2D, center: Point2D.Float,
                               outerRadius: Float, innerRadius: Float,
                               mouseX: Int, mouseY: Int) {
  val outerEllipse = makeCircle(center, outerRadius)
  verifyFillShape(inOrder, g, outerEllipse, ACTION_HANDLE_OUTER_COLOR)

  val innerEllipse = makeCircle(center, innerRadius)
  verifyFillShape(inOrder, g, innerEllipse, ACTION_HANDLE_INNER_COLOR)

  verifyDrawLineToMouse(inOrder, g, center, mouseX, mouseY)
}

fun verifyDrawEmptyDesigner(inOrder: InOrder, g: Graphics2D, point: Point2D.Float) {
  inOrder.apply {
    verify(g).color = EMPTY_DESIGNER_TEXT_COLOR
    verify(g).font = any()
    verify(g).drawString("Click ", point.x.toInt(), point.y.toInt())
    verify(g).fontMetrics
    if (JreHiDpiUtil.isJreHiDPIEnabled()) {
      verify(g).deviceConfiguration
    }
    verify(g).drawString(eq(" to add a destination"), anyInt(), eq(point.y.toInt()))
    verify(g).dispose()
  }
}

fun makeGraphicsMock() : Graphics2D {
  val graphics = Mockito.mock(Graphics2D::class.java)

  val metrics = Mockito.mock(FontMetrics::class.java)
  whenever(graphics.fontMetrics).thenReturn(metrics)
  whenever(graphics.getFontMetrics(any())).thenReturn(metrics)

  val configuration = Mockito.mock(GraphicsConfiguration::class.java)
  whenever(graphics.deviceConfiguration).thenReturn(configuration)
  val device = Mockito.mock(GraphicsDevice::class.java)
  whenever(configuration.device).thenReturn(device)
  val transform = Mockito.mock(AffineTransform::class.java)
  whenever(configuration.defaultTransform).thenReturn(transform)

  return graphics
}

private fun makeCircle(center: Point2D.Float, radius: Float) =
  Ellipse2D.Float(center.x - radius, center.y - radius, 2 * radius, 2 * radius)
