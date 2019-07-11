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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.surface.Layer
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D

internal class ModelNameLayer(private val myScreenView: ScreenViewBase) : Layer() {
  /**
   * A reusable rectangle which is used during painting.
   */
  private val myCachedRectangle = Rectangle()

  override fun paint(g2d: Graphics2D) {
    val modelName = myScreenView.sceneManager.model.modelDisplayName ?: return
    val clipBounds = g2d.clipBounds
    val originalColor = g2d.color

    myScreenView.surface.getRenderableBoundsForInvisibleComponents(myScreenView, myCachedRectangle)
    Rectangle2D.intersect(myCachedRectangle, clipBounds, myCachedRectangle)

    g2d.clip = myCachedRectangle
    g2d.color = JBColor.foreground()

    val font = g2d.font
    val metrics = g2d.getFontMetrics(font)
    val fontHeight = metrics.height

    val x = myScreenView.x
    val y = myScreenView.y - (myScreenView.nameLabelHeight - fontHeight)
    g2d.drawString(modelName, x, y)

    g2d.color = originalColor
    g2d.clip = clipBounds
  }
}
