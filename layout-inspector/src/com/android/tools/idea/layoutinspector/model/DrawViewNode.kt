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

import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.DeviceViewSettings
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.Gray
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BasicStroke.CAP_BUTT
import java.awt.BasicStroke.JOIN_MITER
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private fun getNormalBorderThickness(scale: Double) = 1f.scale(scale)
fun getEmphasizedBorderThickness(scale: Double) = 2f.scale(scale)
fun getFoldThickness(scale: Double) = 2f.scale(scale)
fun getEmphasizedBorderOutlineThickness(scale: Double) = 4f.scale(scale)
fun getLabelFontSize(scale: Double) = 12f.scale(scale)
private fun getDash(scale: Double) = floatArrayOf(10f.scale(scale), 10f.scale(scale))

private val EMPHASIZED_LINE_COLOR = Color(106, 161, 211)
private val SELECTED_LINE_COLOR = Color(24, 134, 247)
private val NORMAL_LINE_COLOR = Gray.get(128, 128)
private val FAINT_LINE_COLOR = Gray.get(212, 128)
private val EMPHASIZED_LINE_OUTLINE_COLOR = Color.white

fun getDashedStroke(thickness: (Double) -> Float, scale: Double) =
  BasicStroke(thickness(scale), CAP_BUTT, JOIN_MITER, 10.0f, getDash(scale), 0f)

private fun getEmphasizedLineStroke(scale: Double) = BasicStroke(getEmphasizedBorderThickness(scale))
private fun getEmphasizedImageLineStroke(scale: Double) = getDashedStroke(::getEmphasizedBorderThickness, scale)
private fun getEmphasizedLineOutlineStroke(scale: Double) = BasicStroke(getEmphasizedBorderOutlineThickness(scale))
private fun getEmphasizedImageLineOutlineStroke(scale: Double) = getDashedStroke(::getEmphasizedBorderOutlineThickness, scale)
private fun getSelectedLineStroke(scale: Double) = getEmphasizedLineStroke(scale)
private fun getSelectedImageLineStroke(scale: Double) = getDashedStroke(::getEmphasizedBorderThickness, scale)
fun getFoldStroke(scale: Double) = getDashedStroke(::getFoldThickness, scale)
private fun getNormalLineStroke(scale: Double) = BasicStroke(getNormalBorderThickness(scale))
private fun getNormalImageLineStroke(scale: Double) = getDashedStroke(::getNormalBorderThickness, scale)

fun getDrawNodeLabelHeight(scale: Double) = getLabelFontSize(scale) * 1.6f + 2 * getNormalBorderThickness(scale)

/**
 * A node in the hierarchy used to paint the device view. This is separate from the basic hierarchy ([ViewNode.children]) since views
 * can do their own painting interleaved with painting their children, and we need to keep track of the order in which the operations
 * happen.
 */
sealed class DrawViewNode(owner: ViewNode) {
  val unfilteredOwner = owner

  fun findFilteredOwner(treeSettings: TreeSettings): ViewNode? =
    unfilteredOwner.findClosestUnfilteredNode(treeSettings)

  val bounds: Shape
    get() = unfilteredOwner.renderBounds

  // Children at the start of the child list that have canCollapse = true will be drawn as part of the parent rather than as separate nodes.
  abstract fun canCollapse(treeSettings: TreeSettings): Boolean
  open val drawWhenCollapsed: Boolean
    get() = true

  abstract fun paint(g2: Graphics2D, model: InspectorModel)
  abstract fun paintBorder(g2: Graphics2D, isSelected: Boolean, isHovered: Boolean, model: InspectorModel,
                           viewSettings: DeviceViewSettings, treeSettings: TreeSettings)

  open fun children(access: ViewNode.ReadAccess): Sequence<DrawViewNode> = sequenceOf()
}

/**
 * A draw view corresponding directly to a ViewNode. Is responsible for painting the border.
 */
class DrawViewChild(owner: ViewNode) : DrawViewNode(owner) {
  override fun canCollapse(treeSettings: TreeSettings): Boolean =
    !unfilteredOwner.isInComponentTree(treeSettings)
  override val drawWhenCollapsed: Boolean
    get() = false

  override fun paint(g2: Graphics2D, model: InspectorModel) {}
  override fun paintBorder(
    g2: Graphics2D,
    isSelected: Boolean,
    isHovered: Boolean,
    model: InspectorModel,
    viewSettings: DeviceViewSettings,
    treeSettings: TreeSettings
  ) {
    val owner = findFilteredOwner(treeSettings) ?: return
    // Draw the outline of the border (the white border around the main view border) if necessary.
    if (isSelected || isHovered) {
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = getEmphasizedLineOutlineStroke(viewSettings.scaleFraction)
      if (viewSettings.drawBorders) {
        g2.draw(bounds)
      }
      if (viewSettings.drawUntransformedBounds) {
        g2.draw(owner.layoutBounds)
      }
    }

    var labelX = 0f
    var countX = 0f
    var labelY = 0f
    var borderWidth = 0f
    val composeView = owner as? ComposeViewNode
    val composeCount = composeView?.recompositions?.count ?: 0
    val highlightCount = composeView?.recompositions?.highlightCount ?: 0f
    var showCount = isSelected && treeSettings.showRecompositions && composeCount > 0
    val showHighlight = treeSettings.showRecompositions && highlightCount > 0f && model.maxHighlight != 0f

    // Draw the label background if necessary (the white border of the label and the label background).
    if (isSelected && viewSettings.drawLabel) {
      g2.font = g2.font.deriveFont(getLabelFontSize(viewSettings.scaleFraction))
      val fontMetrics = g2.fontMetrics
      val textWidth = fontMetrics.stringWidth(owner.unqualifiedName).toFloat()
      val countWidth = if (showCount) fontMetrics.stringWidth(composeCount.toString()).toFloat() else 0f

      val border = if (viewSettings.drawBorders || !viewSettings.drawUntransformedBounds) {
        bounds
      }
      else {
        owner.layoutBounds
      }

      val textHeight = (fontMetrics.maxAscent).toFloat()
      borderWidth = textHeight * 0.3f

      val position = computeLabelPosition(border, textWidth)
      labelX = position.first
      countX = position.second - countWidth - 2f * borderWidth
      labelY = position.third

      // Only draw the recomposition count if there is room and at least 2 * borderWidth between the label and the count:
      showCount = showCount && labelX + textWidth + 2f * borderWidth  < countX - 2f * borderWidth

      g2.draw(Rectangle2D.Float(labelX, labelY - textHeight - 2f * borderWidth, textWidth + 2f * borderWidth,
                                textHeight + 2f * borderWidth))
      if (showCount) {
        g2.draw(Rectangle2D.Float(countX, labelY - textHeight - 2f * borderWidth, countWidth + 2f * borderWidth,
                                  textHeight + 2f * borderWidth))
      }

      g2.color = SELECTED_LINE_COLOR
      val emphasizedBorderThickness = getEmphasizedBorderThickness(viewSettings.scaleFraction)
      g2.fill(Rectangle2D.Float(labelX - emphasizedBorderThickness / 2f,
                                labelY - textHeight - 2f * borderWidth - emphasizedBorderThickness / 2f,
                                textWidth + 2f * borderWidth + emphasizedBorderThickness,
                                textHeight + 2f * borderWidth + emphasizedBorderThickness))
      if (showCount) {
        g2.fill(Rectangle2D.Float(countX - emphasizedBorderThickness / 2f,
                                  labelY - textHeight - 2f * borderWidth - emphasizedBorderThickness / 2f,
                                  countWidth + 2f * borderWidth + emphasizedBorderThickness,
                                  textHeight + 2f * borderWidth + emphasizedBorderThickness))
      }
    }

    // Draw the border
    when {
      isSelected -> {
        g2.color = SELECTED_LINE_COLOR
        g2.stroke = getSelectedLineStroke(viewSettings.scaleFraction)
      }
      isHovered -> {
        g2.color = EMPHASIZED_LINE_COLOR
        g2.stroke = getEmphasizedLineStroke(viewSettings.scaleFraction)
      }
      showHighlight -> {
        g2.color = highlightColor(model, highlightCount, viewSettings)
        g2.stroke = getNormalLineStroke(viewSettings.scaleFraction)
      }
      else -> {
        g2.color = if (treeSettings.showRecompositions) FAINT_LINE_COLOR else NORMAL_LINE_COLOR
        g2.stroke = getNormalLineStroke(viewSettings.scaleFraction)
      }
    }
    if (viewSettings.drawBorders || isHovered || showHighlight || (isSelected && !viewSettings.drawUntransformedBounds)) {
      g2.draw(bounds)
    }
    if (viewSettings.drawUntransformedBounds) {
      g2.draw(owner.layoutBounds)
    }

    // Draw the text of the label if necessary.
    if (isSelected && viewSettings.drawLabel) {
      g2.color = Color.WHITE
      g2.drawString(owner.unqualifiedName, labelX + borderWidth,
                    labelY - borderWidth - getEmphasizedBorderThickness(viewSettings.scaleFraction) / 2f)

      if (showCount) {
        g2.drawString(composeCount.toString(), countX + borderWidth,
                      labelY - borderWidth - getEmphasizedBorderThickness(viewSettings.scaleFraction) / 2f)
      }
    }

    // Draw gradient for recomposition highlights
    if (showHighlight) {
      val rect = bounds.bounds2D
      g2.paint = GradientPaint(
        rect.x.toFloat(), rect.y.toFloat(), heatmapColor(model, highlightCount, viewSettings),
        (rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat(), heatmapColor(model, 0f, viewSettings))
      g2.fill(bounds)
    }
  }

  private fun highlightColor(model: InspectorModel, highlightCount: Float, viewSettings: DeviceViewSettings): Color {
    val baseColor = Color(viewSettings.highlightColor)
    val alpha = ((highlightCount * 255f) / model.maxHighlight).toInt().coerceIn(32, 255)
    return Color(baseColor.red, baseColor.green, baseColor.blue, alpha)
  }

  private fun heatmapColor(model: InspectorModel, highlightCount: Float, viewSettings: DeviceViewSettings): Color {
    val baseColor = Color(viewSettings.highlightColor)
    val alpha = ((highlightCount * 192f) / model.maxHighlight).toInt().coerceIn(8, 192)
    return Color(baseColor.red, baseColor.green, baseColor.blue, alpha)
  }

  /**
   * Compute the position of the label and recomposition count:
   * - find the edge with the least slope where one of the ends is at the minimum y. This is the "top".
   * - find the left side of that segment. The x coordinate of that is the x coordinate of the label.
   * - find the right side of that segment. The x coordinate of that is the right x coordinate of the recomposition count.
   * - find where the bottom edge of the label should meet the edge of the border: This is the minimum of halfway across the edge
   *   and halfway across the label.
   * - find the y coordinate of that using the slope of the line.
   *
   * @return (xLeft, xRight, yTop)
   */
  private fun computeLabelPosition(border: Shape, textWidth: Float): Triple<Float, Float, Float> {
    val minY = border.bounds.minY.toFloat()
    var minSlope = Float.MAX_VALUE
    val nextPoint = FloatArray(6)
    val pathIter = border.getPathIterator(null)
    pathIter.currentSegment(nextPoint)
    var prevX: Float
    var prevY: Float
    var leastSlopedSideWidth = 0f
    var topLeftY = 0f
    var xLeft = 0f
    var xRight = 0f
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
          xLeft = min(nextPoint[0], prevX)
          xRight = max(nextPoint[0], prevX)
          minSlope = slope
          leastSlopedSideWidth = abs(nextPoint[0] - prevX)
          topLeftY = if (nextPoint[0] < prevX) nextPoint[1] else prevY
        }
      }
    }
    val connectionWidth = min(leastSlopedSideWidth, textWidth) / 2f
    return Triple(xLeft, xRight, minSlope * connectionWidth + topLeftY)
  }

  override fun children(access: ViewNode.ReadAccess): Sequence<DrawViewNode> =
    access.run { unfilteredOwner.drawChildren.asSequence() }
}

/**
 * A draw view that paints an image. The `owner` should be the view that does the painting, and is also the "draw parent" of this node.
 */
class DrawViewImage(@get:VisibleForTesting val image: Image, owner: ViewNode, private val deviceClip: Shape? = null) : DrawViewNode(owner) {
  override fun canCollapse(treeSettings: TreeSettings) = true

  override fun paint(g2: Graphics2D, model: InspectorModel) {
    deviceClip?.let { g2.clip(deviceClip) }
    val bounds = bounds.bounds
    UIUtil.drawImage(
      g2, image,
      Rectangle(max(bounds.x, 0), max(bounds.y, 0), bounds.width + min(bounds.x, 0), bounds.height + min(bounds.y, 0)),
      Rectangle(0, 0, image.getWidth(null), image.getHeight(null)), null)
  }

  override fun paintBorder(
    g2: Graphics2D,
    isSelected: Boolean,
    isHovered: Boolean,
    model: InspectorModel,
    viewSettings: DeviceViewSettings,
    treeSettings: TreeSettings
  ) {
    if (isSelected || isHovered) {
      g2.color = EMPHASIZED_LINE_OUTLINE_COLOR
      g2.stroke = getEmphasizedImageLineOutlineStroke(viewSettings.scaleFraction)
      g2.draw(bounds)
    }
    when {
      isSelected -> {
        g2.color = SELECTED_LINE_COLOR
        g2.stroke = getSelectedImageLineStroke(viewSettings.scaleFraction)
      }
      isHovered -> {
        g2.color = EMPHASIZED_LINE_COLOR
        g2.stroke = getEmphasizedImageLineStroke(viewSettings.scaleFraction)
      }
      else -> {
        g2.color = NORMAL_LINE_COLOR
        g2.stroke = getNormalImageLineStroke(viewSettings.scaleFraction)
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
  override fun canCollapse(treeSettings: TreeSettings) = false

  override fun paint(g2: Graphics2D, model: InspectorModel) {
    if (root.layoutBounds.width > 0 && root.layoutBounds.height > 0) {
      val color = g2.color
      g2.color = Color(0.2f, 0.2f, 0.2f, 0.5f)
      g2.fillRect(0, 0, root.layoutBounds.width, root.layoutBounds.height)
      g2.color = color
    }
  }

  override fun paintBorder(
    g2: Graphics2D,
    isSelected: Boolean,
    isHovered: Boolean,
    model: InspectorModel,
    viewSettings: DeviceViewSettings,
    treeSettings: TreeSettings
  ) {
    if (root.layoutBounds.width > 0 && root.layoutBounds.height > 0) {
      g2.color = NORMAL_LINE_COLOR
      g2.stroke = getNormalImageLineStroke(viewSettings.scaleFraction)
      g2.drawRect(0, 0, root.layoutBounds.width, root.layoutBounds.height)
    }
  }
}

private fun Float.scale(scale: Double): Float = JBUIScale.scale(this) / scale.toFloat()
