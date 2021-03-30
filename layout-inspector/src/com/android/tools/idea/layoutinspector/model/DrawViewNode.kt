/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.ui.DeviceViewSettings
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BasicStroke.CAP_BUTT
import java.awt.BasicStroke.JOIN_MITER
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val NORMAL_BORDER_THICKNESS = 1f
private const val EMPHASIZED_BORDER_THICKNESS = 5f
const val EMPHASIZED_BORDER_OUTLINE_THICKNESS = 7f
const val LABEL_FONT_SIZE = 30f
private val DASH = floatArrayOf(20f, 20f)

private val EMPHASIZED_LINE_COLOR = Color(106, 161, 211)
private val EMPHASIZED_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS)
private val EMPHASIZED_IMAGE_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val EMPHASIZED_LINE_OUTLINE_STROKE = BasicStroke(EMPHASIZED_BORDER_OUTLINE_THICKNESS)
private val EMPHASIZED_IMAGE_LINE_OUTLINE_STROKE = BasicStroke(EMPHASIZED_BORDER_OUTLINE_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val SELECTED_LINE_COLOR = Color(24, 134, 247)
private val SELECTED_LINE_STROKE = EMPHASIZED_LINE_STROKE
private val SELECTED_IMAGE_LINE_STROKE = BasicStroke(EMPHASIZED_BORDER_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val NORMAL_LINE_COLOR = JBColor(Gray.get(128, 128), Gray.get(212, 128))
private val NORMAL_LINE_STROKE = BasicStroke(NORMAL_BORDER_THICKNESS)
private val NORMAL_IMAGE_LINE_STROKE = BasicStroke(NORMAL_BORDER_THICKNESS, CAP_BUTT, JOIN_MITER, 10.0f, DASH, 0f)
private val EMPHASIZED_LINE_OUTLINE_COLOR = Color.white

const val DRAW_NODE_LABEL_HEIGHT = LABEL_FONT_SIZE * 1.6f + 2 * NORMAL_BORDER_THICKNESS

/**
 * A node in the hierarchy used to paint the device view. This is separate from the basic hierarchy ([ViewNode.children]) since views
 * can do their own painting interleaved with painting their children, and we need to keep track of the order in which the operations
 * happen.
 */
sealed class DrawViewNode(owner: ViewNode) {
  val unfilteredOwner = owner

  val owner: ViewNode?
    get() = unfilteredOwner.findClosestUnfilteredNode()

  val bounds: Shape
    get() = unfilteredOwner.transformedBounds

  // Children at the start of the child list that have canCollapse = true will be drawn as part of the parent rather than as separate nodes.
  abstract val canCollapse: Boolean
  open val drawWhenCollapsed: Boolean
    get() = true

  abstract fun paint(g2: Graphics2D, model: InspectorModel)
  abstract fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, viewSettings: DeviceViewSettings)

  open fun children(drawChildren: ViewNode.() -> List<DrawViewNode>): Sequence<DrawViewNode> = sequenceOf()
}

/**
 * A draw view corresponding directly to a ViewNode. Is responsible for painting the border.
 */
class DrawViewChild(owner: ViewNode) : DrawViewNode(owner) {
  override val canCollapse: Boolean
    get() = !unfilteredOwner.isInComponentTree
  override val drawWhenCollapsed: Boolean
    get() = false

  override fun paint(g2: Graphics2D, model: InspectorModel) {}
  override fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, viewSettings: DeviceViewSettings) {
    val owner = owner ?: return
    // Draw the outline of the border (the white border around the main view border) if necessary.
    if (isSelected || isHovered) {
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = EMPHASIZED_LINE_OUTLINE_STROKE
      if (viewSettings.drawBorders) {
        g2.draw(bounds)
      }
      if (viewSettings.drawUntransformedBounds) {
        g2.draw(owner.layoutBounds)
      }
    }

    var labelX = 0f
    var labelY = 0f
    var borderWidth = 0f

    // Draw the label background if necessary (the white border of the label and the label background).
    if (isSelected && viewSettings.drawLabel) {
      g2.font = g2.font.deriveFont(JBUIScale.scale(LABEL_FONT_SIZE))
      val fontMetrics = g2.fontMetrics
      val textWidth = fontMetrics.stringWidth(owner.unqualifiedName).toFloat()

      val border = if (viewSettings.drawBorders || (isSelected && !viewSettings.drawUntransformedBounds)) {
        bounds
      }
      else {
        owner.layoutBounds
      }

      val position = computeLabelPosition(border, textWidth)
      labelX = position.first
      labelY = position.second

      val textHeight = (fontMetrics.maxAscent).toFloat()
      borderWidth = textHeight * 0.3f
      g2.draw(Rectangle2D.Float(labelX, labelY - textHeight - 2f * borderWidth, textWidth + 2f * borderWidth, textHeight + 2f * borderWidth))

      g2.color = SELECTED_LINE_COLOR
      g2.fill(Rectangle2D.Float(labelX - EMPHASIZED_BORDER_THICKNESS / 2f,
                                labelY - textHeight - 2f * borderWidth - EMPHASIZED_BORDER_THICKNESS / 2f,
                                textWidth + 2f * borderWidth + EMPHASIZED_BORDER_THICKNESS,
                                textHeight + 2f * borderWidth + EMPHASIZED_BORDER_THICKNESS))
    }

    // Draw the border
    when {
      isSelected -> {
        g2.color = SELECTED_LINE_COLOR
        g2.stroke = SELECTED_LINE_STROKE
      }
      isHovered -> {
        g2.color = EMPHASIZED_LINE_COLOR
        g2.stroke = EMPHASIZED_LINE_STROKE
      }
      else -> {
        g2.color = NORMAL_LINE_COLOR
        g2.stroke = NORMAL_LINE_STROKE
      }
    }
    if (viewSettings.drawBorders || isHovered || (isSelected && !viewSettings.drawUntransformedBounds)) {
      g2.draw(bounds)
    }
    if (viewSettings.drawUntransformedBounds) {
      g2.draw(owner.layoutBounds)
    }

    // Draw the text of the label if necessary.
    if (isSelected && viewSettings.drawLabel) {
      g2.color = Color.WHITE
      g2.drawString(owner.unqualifiedName, labelX + borderWidth, labelY - borderWidth - EMPHASIZED_BORDER_THICKNESS / 2f)
    }
  }

  /**
   * Compute the position of the label:
   * - find the edge with the least slope where one of the ends is at the minimum y. This is the "top".
   * - find the left side of that segment. The x coordinate of that is the x coordinate of the label.
   * - find where the bottom edge of the label should meet the edge of the border: This is the minimum of half way across the edge
   *   and half way across the label.
   * - find the y coordinate of that using the slope of the line.
   */
  private fun computeLabelPosition(border: Shape, textWidth: Float): Pair<Float, Float> {
    val minY = border.bounds.minY.toFloat()
    var minSlope = Float.MAX_VALUE
    val nextPoint = FloatArray(6)
    val pathIter = border.getPathIterator(null)
    pathIter.currentSegment(nextPoint)
    var prevX: Float
    var prevY: Float
    var leastSlopedSideWidth = 0f
    var topLeftY = 0f
    var x = 0f
    while (true) {
      prevX = nextPoint[0]
      prevY = nextPoint[1]
      pathIter.next()
      if (pathIter.isDone) {
        break
      }
      pathIter.currentSegment(nextPoint)
      if (prevY == minY || nextPoint[1] == minY) {
        if (abs(prevX - nextPoint[0]) < 0.001) {
          continue
        }
        val slope = (nextPoint[1] - prevY) / (nextPoint[0] - prevX)
        if (abs(slope) < abs(minSlope)) {
          x = min(nextPoint[0], prevX)
          minSlope = slope
          leastSlopedSideWidth = abs(nextPoint[0] - prevX)
          topLeftY = if (nextPoint[0] < prevX) nextPoint[1] else prevY
        }
      }
    }
    val connectionWidth = min(leastSlopedSideWidth, textWidth) / 2f
    return Pair(x, minSlope * connectionWidth + topLeftY)
  }

  override fun children(drawChildren: ViewNode.() -> List<DrawViewNode>): Sequence<DrawViewNode> =
    unfilteredOwner.drawChildren().asSequence()
}

/**
 * A draw view that paints an image. The [owner] should be the view that does the painting, and is also the "draw parent" of this node.
 */
class DrawViewImage(@VisibleForTesting val image: Image, owner: ViewNode) : DrawViewNode(owner) {
  override val canCollapse = true

  override fun paint(g2: Graphics2D, model: InspectorModel) {
    val bounds = bounds.bounds
    UIUtil.drawImage(
      g2, image,
      Rectangle(max(bounds.x, 0), max(bounds.y, 0), bounds.width + min(bounds.x, 0), bounds.height + min(bounds.y, 0)),
      Rectangle(0, 0, image.getWidth(null), image.getHeight(null)), null)
  }

  override fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, viewSettings: DeviceViewSettings) {
    if (isSelected || isHovered) {
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = EMPHASIZED_IMAGE_LINE_OUTLINE_STROKE
      g2.draw(bounds)
    }
    when {
      isSelected -> {
        g2.color = SELECTED_LINE_COLOR
        g2.stroke = SELECTED_IMAGE_LINE_STROKE
      }
      isHovered -> {
        g2.color = EMPHASIZED_LINE_COLOR
        g2.stroke = EMPHASIZED_IMAGE_LINE_STROKE
      }
      else -> {
        g2.color = NORMAL_LINE_COLOR
        g2.stroke = NORMAL_IMAGE_LINE_STROKE
      }
    }
    g2.draw(bounds)
  }
}

/**
 * A draw view that draw a semi-transparent grey rectangle. Shown when a window has DIM_BEHIND set and is drawn over another window (e.g.
 * a dialog box).
 */
class Dimmer(val root: ViewNode) : DrawViewNode(root) {
  override val canCollapse = false

  override fun paint(g2: Graphics2D, model: InspectorModel) {
    if (root.width > 0 && root.height > 0) {
      val color = g2.color
      g2.color = Color(0.2f, 0.2f, 0.2f, 0.5f)
      g2.fillRect(0, 0, root.width, root.height)
      g2.color = color
    }
  }

  override fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, viewSettings: DeviceViewSettings) {
    if (root.width > 0 && root.height > 0) {
      g2.color = NORMAL_LINE_COLOR
      g2.stroke = NORMAL_IMAGE_LINE_STROKE
      g2.drawRect(0, 0, root.width, root.height)
    }
  }
}