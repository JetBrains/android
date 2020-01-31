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

import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.surface.Layer
import com.intellij.ui.JBColor
import java.awt.Graphics2D
import java.awt.Rectangle

internal class ModelNameLayer(private val myScreenView: ScreenViewBase) : Layer() {
  /**
   * A reusable rectangle which is used during painting.
   */
  private val myCachedRectangle = Rectangle()

  override fun paint(originalGraphics: Graphics2D) {
    val modelName = myScreenView.sceneManager.model.modelDisplayName ?: return
    val g2d = originalGraphics.create() as Graphics2D
    try {
      myScreenView.surface.getRenderableBoundsForInvisibleComponents(myScreenView, myCachedRectangle)
      g2d.clip(myCachedRectangle)
      g2d.color = JBColor.foreground()

      val font = g2d.font
      val fontHeight = g2d.getFontMetrics(font).height

      val x = myScreenView.x
      val y = myScreenView.y - (myScreenView.nameLabelHeight - fontHeight)
      g2d.setRenderingHints(HQ_RENDERING_HINTS)
      g2d.drawString(modelName, x, y)
    }
    finally {
      g2d.dispose()
    }
  }
}
