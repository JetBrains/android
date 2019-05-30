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
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon

class TemplateIcon(private val myDelegateIcon: Icon) : JBUI.ScalableJBIcon() {
  private var myScale = 1f
  private var myCropRectangle: Rectangle = Rectangle(myDelegateIcon.iconWidth, myDelegateIcon.iconHeight)

  private fun setScale(scale: Float) {
    myScale = scale
    myCropRectangle = Rectangle((myCropRectangle.x * myScale).toInt(), (myCropRectangle.y * myScale).toInt(),
                                (myCropRectangle.width * myScale).toInt(), (myCropRectangle.height * myScale).toInt())
  }

  fun setHeight(height: Int) {
    setScale(height.toFloat() / myCropRectangle.height)
  }

  fun cropBlankWidth() {
    val image = ImageUtil.toBufferedImage(IconUtil.toImage(myDelegateIcon), true)
    ImageUtils.getCropBounds(image, ImageUtils.TRANSPARENCY_FILTER, null)?.run {
      myCropRectangle.x = this.x
      myCropRectangle.width = this.width
    }
  }

  override fun scale(scale: Float): Icon {
    setScale(scale)
    return this
  }

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val icon = IconUtil.scale(myDelegateIcon, c, myScale)
    icon.paintIcon(c, g, x - myCropRectangle.x, y)
  }

  override fun getIconWidth(): Int = myCropRectangle.width

  override fun getIconHeight(): Int = myCropRectangle.height
}
