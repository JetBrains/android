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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.ui.resourcemanager.RESOURCE_DEBUG
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.SwingConstants

internal val EMPTY_IMAGE = createIcon(if (RESOURCE_DEBUG) JBColor.GREEN else Color(0, 0, 0, 0))
internal val ERROR_IMAGE = createIcon(if (RESOURCE_DEBUG) JBColor.RED else Color(10, 10, 10, 10))

internal fun createIcon(color: Color?): BufferedImage = UIUtil.createImage(
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
  val image =  BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_INT_ARGB)
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
  return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
    paintDrawablePlaceholderImage(createGraphics(), width, height)
  }
}

private fun paintDrawablePlaceholderImage(g: Graphics2D, width: Int, height: Int) {
  val label = JBLabel(StudioIcons.Shell.ToolWindows.VISUAL_ASSETS, SwingConstants.CENTER).apply {
    bounds = Rectangle(0, 0, width, height)
    validate()
  }
  label.paint(g)
  g.dispose()
}

internal fun createLayoutPlaceholderImage(width: Int, height: Int): BufferedImage {
  return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
    paintLayoutPlaceHolderImage(createGraphics(), width, height)
  }
}

private fun paintLayoutPlaceHolderImage(g: Graphics2D, width: Int, height: Int) {
  val ratio = 3f / 4f
  if (height < 10 || width < (height * ratio).toInt()) return // Not enough space, don't paint anything.

  val cardHeight = 10.coerceAtLeast((height * 0.9f).toInt())
  val cardWidth = (cardHeight * ratio).toInt()
  val cardX = (width * 0.5f + 0.5f).toInt() - (cardWidth * 0.5f + 0.5f).toInt()
  val cardY = (height * 0.5f + 0.5f).toInt() - (cardHeight * 0.5f + 0.5f).toInt()

  val cardCornerRadius = JBUIScale.scale(5)

  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
  // Paint card background
  g.color = JBColor.PanelBackground
  g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, cardCornerRadius, cardCornerRadius)
  // Paint card border
  g.color = JBColor(Gray._192, Gray._144)
  g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, cardCornerRadius, cardCornerRadius)

  g.color = JBColor(Gray._168, Gray._92)
  val shapeOffset = (cardHeight / 11f + 0.5f).toInt()
  val circleLength = (shapeOffset * 1.4f + 0.5f).toInt()
  val longRectangleWidth = cardWidth - (shapeOffset * 2)
  val shortRectangleWidth = cardWidth - (shapeOffset * 4)
  val rectangleHeight = (shapeOffset * 0.8f + 0.5f).toInt()
  val rectangleMargin = (cardWidth * 0.1f + 0.5f).toInt()
  val shapeX = cardX + rectangleMargin
  for (j in 0 until 5) {
    // Paint some shapes that looks like a generic android layout
    val shapeY = cardY + shapeOffset + (shapeOffset * (j * 2))
    when (j) {
      0 -> g.fillOval(shapeX, shapeY, circleLength, circleLength)
      1, 4 -> g.fillRect(shapeX, shapeY, longRectangleWidth, rectangleHeight)
      2, 3 -> g.fillRect(shapeX, shapeY, shortRectangleWidth, rectangleHeight)
    }
  }
  g.dispose()
}