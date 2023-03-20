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
package com.android.tools.adtui.ui

import com.android.tools.adtui.ImageUtils
import com.intellij.ui.components.JBPanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.drawImage
import java.awt.AlphaComposite
import java.awt.Composite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Rectangle
import kotlin.math.roundToInt

/**
 * A [JBPanel] that shows an [Image] as background, scaled to fit preserving the aspect ratio.
 * Quality of scaling is controlled by the [highFidelityScaling] parameter.
 */
class ImagePanel(private val highFidelityScaling: Boolean = false) : JBPanel<ImagePanel>(true) {
  var image: Image? = null
    set(value) {
      field = value
      repaint()
    }

  var active: Boolean = true
    set(value) {
      field = value
      repaint()
    }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintPanelImage(g, image, active, highFidelityScaling)
  }
}

/**
 * Draws an image inside a [JBPanel], preserving aspect ratio.
 */
fun <T: JBPanel<T>> JBPanel<T>.paintPanelImage(g: Graphics, image: Image?, active: Boolean, highFidelityScaling: Boolean) {
  val insets = this.insets
  val rect = Rectangle(insets.left, insets.top, width - insets.left - insets.right, height - insets.bottom - insets.top)

  // Paint background.
  g.color = background
  g.fillRect(rect.x, rect.y, rect.width, rect.height)

  // Set Alpha to "light" if component is not active.
  var prevComposite: Composite? = null
  if (!active) {
    (g as Graphics2D?)?.apply {
      prevComposite = composite
      composite = AlphaComposite.SrcOver.derive(0.3f)
    }
  }

  // Draw image, centered, preserving the aspect ratio.
  image?.let { img ->
    val imageWidth = img.getWidth(this)
    val imageHeight = img.getHeight(this)
    if (imageWidth > 0 && imageHeight > 0) {
      val scale = (rect.getWidth() / imageWidth).coerceAtMost(rect.getHeight() / imageHeight)
      val w = (imageWidth * scale).roundToInt()
      val h = (imageHeight * scale).roundToInt()
      val xOffset = rect.x + (rect.width - w) / 2
      val yOffset = rect.y + (rect.height - h) / 2
      if (highFidelityScaling) {
        val bufferedImage = ImageUtil.toBufferedImage(img)
        val graphicsScale = JBUIScale.sysScale(g as Graphics2D).toDouble()
        val scaleX = graphicsScale * w / imageWidth
        val scaleY = graphicsScale * h / imageHeight
        // ImageUtils.scale uses a two-step scaling algorithm that produces better quality results
        // than the standard AWT scaling.
        val scaledImage = ImageUtils.scale(bufferedImage, scaleX, scaleY)
        g.drawImage(scaledImage, xOffset, yOffset, w, h, null)
      }
      else {
        // Draw the image using IJ wrapper so that rendering is optimized for JBHiDPIScaledImage
        drawImage(g = g, image = img, x = xOffset, y = yOffset, dw = w, dh = h)
      }
    }
  }

  // Restore composite.
  (g as Graphics2D?)?.apply {
    prevComposite?.let { composite = it }
  }
}
