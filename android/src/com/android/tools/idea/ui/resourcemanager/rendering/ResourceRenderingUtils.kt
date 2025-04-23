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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.ui.resourcemanager.RESOURCE_DEBUG
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.SwingConstants
import kotlin.math.max

internal val EMPTY_IMAGE = createIcon(if (RESOURCE_DEBUG) JBColor.GREEN else Color(0, 0, 0, 0))
internal val ERROR_IMAGE = createIcon(if (RESOURCE_DEBUG) JBColor.RED else Color(10, 10, 10, 10))

internal fun createIcon(color: Color?): BufferedImage = ImageUtil.createImage(
  80, 80, BufferedImage.TYPE_INT_ARGB
).apply {
  with(createGraphics()) {
    this.color = color
    fillRect(0, 0, 80, 80)
    dispose()
  }
}

internal fun createFailedIcon(dimension: Dimension): BufferedImage {
  @Suppress("UndesirableClassUsage") // Dimensions for BufferedImage are pre-scaled.
  val image = BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_INT_ARGB)
  val label = JBLabel("Failed preview", StudioIcons.Common.WARNING, SwingConstants.CENTER).apply {
    verticalTextPosition = SwingConstants.BOTTOM
    horizontalTextPosition = SwingConstants.CENTER
    foreground = AdtUiUtils.DEFAULT_FONT_COLOR
    bounds = Rectangle(0, 0, dimension.width, dimension.height)
    validate()
  }
  image.createGraphics().let { g ->
    val labelFont = JBUI.Fonts.label(10f)
    val stringWidth = labelFont.getStringBounds(label.text, g.fontRenderContext).width
    val targetWidth = dimension.width - JBUI.scale(4) // Add some minor padding
    val scale = minOf(targetWidth.toFloat() / stringWidth.toFloat(), 1f) // Only scale down to fit.
    label.font = labelFont.deriveFont(scale * labelFont.size)
    label.paint(g)
    g.dispose()
  }
  return image
}

internal fun createDrawablePlaceholderImage(width: Int, height: Int): BufferedImage {
  return createImageAndPaint(width, height) {
    paintDrawablePlaceholderImage(it, width, height)
  }
}

private fun paintDrawablePlaceholderImage(g: Graphics2D, width: Int, height: Int) {
  val label = JBLabel(StudioIcons.Shell.ToolWindows.VISUAL_ASSETS, SwingConstants.CENTER).apply {
    bounds = Rectangle(0, 0, width, height)
    validate()
  }
  label.paint(g)
}

internal fun createLayoutPlaceholderImage(width: Int, height: Int): BufferedImage {
  return createImageAndPaint(width, height) {
    paintLayoutPlaceHolderImage(it, width, height)
  }
}

private val LAYOUT_PH_BAR_BACKGROUND = Color(0xBF808080.toInt(), true)
private val LAYOUT_PH_BAR_BACKGROUND_ALT = Color(0xBFb4b4b4.toInt(), true)
private val LAYOUT_PH_BACKGROUND = Color(0xBFffffff.toInt(), true)
private val LAYOUT_PH_BORDER_COLOR = Color(0xBF505050.toInt(), true)
private val LAYOUT_PH_FOREGROUND = Color(0xBFffffff.toInt(), true)

private fun paintLayoutPlaceHolderImage(g: Graphics2D, width: Int, height: Int) {
  val ratio = 3f / 4f
  if (height < 10 || width < (height * ratio).toInt()) return // Not enough space, don't paint anything.

  val screenHeight = 10.coerceAtLeast((height * 0.90f).toInt())
  val screenWidth = (screenHeight * ratio).toInt()
  val screenX = (width * 0.5f + 0.5f).toInt() - (screenWidth * 0.5f + 0.5f).toInt()
  val screenY = (height * 0.5f + 0.5f).toInt() - (screenHeight * 0.5f + 0.5f).toInt()
  paintFakeLayout(g, screenWidth, screenHeight, screenX, screenY)
}

private fun paintFakeLayout(g: Graphics2D, width: Int, height: Int, x: Int, y: Int) {
  val barHeight = (width * 0.18f + 0.5f).toInt()
  val bottomBarY = height - barHeight + y
  val bottomBarSegmentSize = (width * 0.33f + 0.5f).toInt()

  val iconSize = (barHeight * 0.6f + 0.5f).toInt()
  val iconXOffset = (width * 0.04f + 0.5f).toInt()
  val iconY = (barHeight * 0.2f + 0.5f).toInt() + y

  val arrow = StudioIcons.Common.BACK_ARROW.toScaledImage(iconSize) // TODO(b/151154027): Try to paint icon in white.
  val arrowX = x + iconXOffset

  val overflow = StudioIcons.Common.OVERFLOW.toScaledImage(iconSize) // TODO(b/151154027): Try to paint icon in white.
  val overflowX = x + width - iconXOffset - iconSize

  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

  // Paint background
  g.color = LAYOUT_PH_BACKGROUND
  g.fillRect(x, y, width, height)

  // Paint top toolbar
  g.color = LAYOUT_PH_BAR_BACKGROUND
  g.fillRect(x, y, width, barHeight)
  g.color = LAYOUT_PH_FOREGROUND
  g.drawImage(arrow, null, arrowX, iconY)
  g.drawImage(overflow, null, overflowX, iconY)

  // Paint bottom nav bar
  g.color = LAYOUT_PH_BAR_BACKGROUND_ALT
  g.fillRect(x, bottomBarY, width, barHeight)
  g.color = LAYOUT_PH_BAR_BACKGROUND
  g.fillRect(x, bottomBarY, bottomBarSegmentSize, barHeight)
  g.fillRect(x + width - bottomBarSegmentSize, bottomBarY, bottomBarSegmentSize, barHeight)

  // Paint border
  g.color = LAYOUT_PH_BORDER_COLOR
  g.stroke = BasicStroke(JBUIScale.scale(2f))
  g.drawRect(x, y, width, height)
}

internal fun createNavigationPlaceHolder(width: Int, height: Int, layoutImage: BufferedImage?): BufferedImage {
  return createImageAndPaint(width, height) {
    paintNavigationPlaceHolder(it, width, height, layoutImage)
  }
}

private const val SIZE_TO_HEIGHT_CONSTANT = 0.866f // from 'h = sqrt(3)/2 * a' for an equilateral triangle

private fun paintNavigationPlaceHolder(g: Graphics2D, width: Int, height: Int, layoutImage: BufferedImage?) {
  val ratio = 3f / 4f
  if (height < 10 || width < (height * ratio).toInt()) return // Not enough space, don't paint anything.

  val arrowColor = Color(0xBF505050.toInt(), true)

  val layoutHeight = (height * 0.8f).toInt().coerceAtLeast(10)
  val layoutWidth = (layoutHeight * ratio).toInt()

  val layoutX = (width * 0.05f + 0.5f).toInt()
  val layoutY = (height * 0.5f + 0.5f).toInt() - (layoutHeight * 0.5f + 0.5f).toInt()

  val image = layoutImage?.scaleTo(layoutWidth, layoutHeight)

  val arrowMargin = (width * 0.1f + 0.5f).toInt()
  val arrowSeparation = (height * 0.18f + 0.5f).toInt()
  val arrowTipUnscaledSize = (arrowSeparation * 0.3f + 0.5f).toInt().coerceAtLeast(6)
  val arrowTipHeight = (arrowTipUnscaledSize * SIZE_TO_HEIGHT_CONSTANT + 0.5f).toInt()
  val arrow1LineStartY = layoutY + (layoutHeight * 0.5f + 0.5f).toInt()
  val arrow1LineStartX = layoutX + layoutWidth
  val arrowLineEndX = (width - JBUIScale.scale(arrowTipHeight)).coerceAtMost(
    arrow1LineStartX + (layoutWidth * 0.6f + 0.2f).toInt())
  val arrow2LineStartX = ((arrow1LineStartX + arrowLineEndX) * 0.5f + 0.5f).toInt()
  val arrow2LineStartY = arrow1LineStartY + arrowSeparation
  val arrowEndX = width - arrowMargin
  val arrow1X = arrowLineEndX
  val arrow1Y = arrow1LineStartY - (JBUIScale.scale(arrowTipUnscaledSize) * 0.5f + 0.5f).toInt()
  val arrow2X = arrowLineEndX
  val arrow2Y = arrow1Y + arrowSeparation

  val arrowPoly = Polygon(arrayOf(0, arrowTipHeight, 0).map(JBUIScale::scale).toIntArray(),
                          arrayOf(0, (arrowTipUnscaledSize * 0.5f + 0.5f).toInt(), arrowTipUnscaledSize).map(JBUIScale::scale).toIntArray(),
                          3)

  val connectionStrokeSize = (arrowSeparation * 0.1f + 0.5f).toInt().coerceAtLeast(2).toFloat().let(JBUIScale::scale)

  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
  g.color = arrowColor

  // Paint arrow 1
  g.stroke = BasicStroke(connectionStrokeSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
  g.drawLine(arrow1LineStartX, arrow1LineStartY, arrowLineEndX, arrow1LineStartY)
  arrowPoly.translate(arrow1X, arrow1Y)
  g.fillPolygon(arrowPoly)
  arrowPoly.translate(-arrow1X, -arrow1Y)

  // Paint connection for arrows
  g.stroke = BasicStroke(connectionStrokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER)
  g.drawLine(arrow2LineStartX, arrow1LineStartY, arrow2LineStartX, arrow2LineStartY)

  // Paint arrow 2
  g.stroke = BasicStroke(connectionStrokeSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
  g.drawLine(arrow2LineStartX, arrow2LineStartY, arrowLineEndX, arrow2LineStartY)
  arrowPoly.translate(arrow2X, arrow2Y)
  g.fillPolygon(arrowPoly)
  arrowPoly.translate(-arrow2X, -arrow2Y)

  // Paint layout image/placeholder
  if (image == null) {
    paintFakeLayout(g, layoutWidth, layoutHeight, layoutX, layoutY)
  }
  else {
    g.drawImage(image, null, layoutX, layoutY)
  }
}

private fun createImageAndPaint(width: Int, height: Int, doPaint: (Graphics2D) -> Unit): BufferedImage {
  return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
    val g = createGraphics()
    try {
      doPaint(g)
    }
    finally {
      g.dispose()
    }
  }
}

private fun BufferedImage.scaleTo(width: Int, height: Int): BufferedImage {
  return ImageUtil.toBufferedImage(ImageUtil.scaleImage(this, width, height))
}

private fun Icon.toScaledImage(size: Int): BufferedImage {
  val scale = size.toDouble() / max(this.iconWidth, this.iconHeight)
  return ImageUtils.scale(ImageUtils.iconToImage(this), scale)
}