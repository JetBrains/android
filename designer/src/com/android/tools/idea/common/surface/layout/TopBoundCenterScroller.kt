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
package com.android.tools.idea.common.surface.layout

import com.android.tools.adtui.common.SwingCoordinate
import java.awt.Dimension
import java.awt.Point

/**
 * When the view size is changed, use the center of top bound as the anchor to keep the scrolling position after zooming.
 */
class TopBoundCenterScroller(@SwingCoordinate private val oldViewSize: Dimension,
                             @SwingCoordinate private val scrollPosition: Point) : DesignSurfaceViewportScroller {
  override fun scroll(port: DesignSurfaceViewport) {
    // the preferred size would be the actual canvas size in viewport.
    val newViewSize = port.viewComponent.preferredSize
    if (newViewSize.width == 0 || newViewSize.height == 0) {
      return
    }
    val portSize = port.extentSize

    if (oldViewSize.width == 0 || oldViewSize.height == 0) {
      // The view was not visible before, no need to change the scroll position.
      return
    }

    val newViewPositionX = if (portSize.width >= newViewSize.width) 0 else {
      val halfPortWidth = portSize.width / 2
      val oldXCenter = scrollPosition.x + halfPortWidth
      val xWeight = oldXCenter.toDouble() / oldViewSize.width
      maxOf(0, (xWeight * newViewSize.width).toInt() - halfPortWidth)
    }

    val newViewPositionY = if (portSize.height >= newViewSize.height) 0 else {
      val oldY = scrollPosition.y
      val oldHeight = oldViewSize.height
      val yWeight = oldY.toDouble() / oldHeight
      (yWeight * newViewSize.height).toInt()
    }

    port.viewPosition = Point(newViewPositionX, newViewPositionY)
  }
}
