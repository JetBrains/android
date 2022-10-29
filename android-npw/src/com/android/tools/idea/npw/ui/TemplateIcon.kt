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
package com.android.tools.idea.npw.ui

import com.android.tools.adtui.ImageUtils
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBScalableIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon

class TemplateIcon(private val delegateIcon: Icon) : JBScalableIcon() {
  private var _scale = 1f
  private var cropRectangle = Rectangle(delegateIcon.iconWidth, delegateIcon.iconHeight)

  fun setHeight(height: Int) = scale(height.toFloat() / cropRectangle.height)

  fun cropBlankWidth() {
    val image = ImageUtil.toBufferedImage(IconUtil.toImage(delegateIcon), true)
    ImageUtils.getCropBounds(image, ImageUtils.TRANSPARENCY_FILTER, null)?.run {
      cropRectangle.x = x
      cropRectangle.width = width
    }
  }

  override fun scale(scale: Float): Icon = apply {
    _scale = scale
    cropRectangle = Rectangle((cropRectangle.x * scale).toInt(), (cropRectangle.y * scale).toInt(),
                              (cropRectangle.width * scale).toInt(), (cropRectangle.height * scale).toInt())
  }

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    delegateIcon.paintIcon(c, g, x - cropRectangle.x, y)
  }

  override fun getIconWidth() = cropRectangle.width

  override fun getIconHeight() = cropRectangle.height
}
