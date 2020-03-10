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
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.SwingConstants
import kotlin.math.max

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

  val barHeight = (screenHeight * 0.18f + 0.5f).toInt()
  val bottomBarY = screenHeight - barHeight + screenY
  val bottomBarSegmentSize = (screenWidth * 0.33f + 0.5f).toInt()

  val iconSize = (barHeight * 0.6f + 0.5f).toInt()
  val iconXOffset = (screenWidth * 0.04f + 0.5f).toInt()
  val iconY = (barHeight * 0.2f + 0.5f).toInt() + screenY

  val arrow = StudioIcons.Common.BACK_ARROW.toScaledImage(iconSize) // TODO(b/151154027): Try to paint icon in white.
  val arrowX = screenX + iconXOffset

  val overflow = StudioIcons.Common.OVERFLOW.toScaledImage(iconSize) // TODO(b/151154027): Try to paint icon in white.
  val overflowX = screenX + screenWidth - iconXOffset - iconSize

  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

  // Paint background
  g.color = LAYOUT_PH_BACKGROUND
  g.fillRect(screenX, screenY, screenWidth, screenHeight)

  // Paint top toolbar
  g.color = LAYOUT_PH_BAR_BACKGROUND
  g.fillRect(screenX, screenY, screenWidth, barHeight)
  g.color = LAYOUT_PH_FOREGROUND
  g.drawImage(arrow, null, arrowX, iconY)
  g.drawImage(overflow, null, overflowX, iconY)

  // Paint bottom nav bar
  g.color = LAYOUT_PH_BAR_BACKGROUND_ALT
  g.fillRect(screenX, bottomBarY, screenWidth, barHeight)
  g.color = LAYOUT_PH_BAR_BACKGROUND
  g.fillRect(screenX, bottomBarY, bottomBarSegmentSize, barHeight)
  g.fillRect(screenX + screenWidth - bottomBarSegmentSize, bottomBarY, bottomBarSegmentSize, barHeight)

  // Paint border
  g.color = LAYOUT_PH_BORDER_COLOR
  g.stroke = BasicStroke(JBUIScale.scale(2f))
  g.drawRect(screenX, screenY, screenWidth, screenHeight)
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

private fun Icon.toScaledImage(size: Int): BufferedImage {
  val scale = size.toDouble() / max(this.iconWidth, this.iconHeight)
  return ImageUtils.scale(ImageUtils.iconToImage(this), scale)
}