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
package com.android.tools.idea.adb.wireless

import com.intellij.ui.components.JBPanel
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle

/**
 * A [JBPanel] that shows an [Image] as background, scaled with no interpolation, and preserving aspect ratio
 */
class ImagePanel : JBPanel<ImagePanel>(true) {
  var image: Image? = null
    set(value) {
      field = value
      invalidate()
    }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val insets = this.insets
    val rect = Rectangle(insets.left, insets.top, width - insets.left - insets.right, height - insets.bottom - insets.top)

    // Paint background
    g.color = background
    g.fillRect(rect.x, rect.y, rect.width, rect.height)

    // Draw image, centered, keeping aspect ratio
    image?.let { img ->
      val size = rect.width.coerceAtMost(rect.height) // min(width, height)
      if (size > 0) {
        val xOffset = rect.x + offsetFor(rect.width, rect.height)
        val yOffset = rect.y + offsetFor(rect.height, rect.width)
        g.drawImage(img, xOffset, yOffset, size, size, null)
      }
    }
  }

  private fun offsetFor(a: Int, b: Int) = if (a > b) { (a - b) / 2 } else { 0 }
}
