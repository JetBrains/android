/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.layer

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.surface.ScreenView
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * Layer to buildDisplayList the canvas resizing cue in the bottom-right corner of the screen view.
 */
class CanvasResizeLayer(private val screenView: ScreenView, private val repaint: () -> Unit) :
  Layer() {
  private var isHovering = false

  /**
   * Sets the state of this layer according to the mouse hovering at point (x, y). Returns whether
   * that required any modification to the state of the layer.
   */
  override fun onHover(@SwingCoordinate x: Int, @SwingCoordinate y: Int) {
    val oldHovering = isHovering
    val size = screenView.scaledContentSize
    val resizeZone =
      Rectangle(
        screenView.x + size.width,
        screenView.y + size.height,
        NlConstants.RESIZING_HOVERING_SIZE,
        NlConstants.RESIZING_HOVERING_SIZE,
      )
    isHovering = resizeZone.contains(x, y)
    if (isHovering != oldHovering) {
      repaint()
    }
  }

  override fun paint(g2d: Graphics2D) {
    val size = screenView.scaledContentSize
    val x = screenView.x
    val y = screenView.y
    val graphics = g2d.create() as Graphics2D
    graphics.stroke = NlConstants.SOLID_STROKE
    graphics.color =
      if (isHovering) NlConstants.RESIZING_CORNER_COLOR else NlConstants.RESIZING_CUE_COLOR
    graphics.drawLine(
      x + size.width + NlConstants.BOUNDS_RECT_DELTA,
      y + size.height + 4,
      x + size.width + 4,
      y + size.height + NlConstants.BOUNDS_RECT_DELTA,
    )
    graphics.drawLine(
      x + size.width + NlConstants.BOUNDS_RECT_DELTA,
      y + size.height + 12,
      x + size.width + 12,
      y + size.height + NlConstants.BOUNDS_RECT_DELTA,
    )
    graphics.dispose()
  }

  override val isVisible: Boolean
    get() = screenView.scene.isResizeAvailable
}
