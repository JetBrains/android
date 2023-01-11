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
 * When the view size is changed, the new center position should have same weight in both x-axis and y-axis as before.
 * Consider the size of the view is 1000 * 2000 and the zoom center is at (800, 1500). So the weight is 0.8 on x-axis and 0.75 on
 * y-axis.
 * When view size changes to 500 * 1000, the new center should be (400, 750) because we want to keep same weights
 * We calculate the new viewport position to achieve above behavior.
 */
class ZoomCenterScroller(@SwingCoordinate private val oldViewSize: Dimension,
                         @SwingCoordinate private val scrollPosition: Point,
                         @SwingCoordinate private val zoomCenter: Point) : DesignSurfaceViewportScroller {
  override fun scroll(port: DesignSurfaceViewport) {
    val newViewSize = port.viewSize
    if (newViewSize.width == 0 || newViewSize.height == 0 || newViewSize == oldViewSize) {
      return
    }

    val weightInPaneX: Double = (scrollPosition.x + zoomCenter.x) / oldViewSize.width.toDouble()
    val weightInPaneY: Double = (scrollPosition.y + zoomCenter.y) / oldViewSize.height.toDouble()

    val newViewWidth = newViewSize.width
    val newViewHeight = newViewSize.height
    val newZoomCenterInViewX = newViewWidth * weightInPaneX
    val newZoomCenterInViewY = newViewHeight * weightInPaneY

    // Make sure the view port position doesn't go out of bound. (It may happen when zooming-out)
    val newViewPositionX = (newZoomCenterInViewX - zoomCenter.x).toInt().coerceIn(0, newViewWidth - port.viewportComponent.width)
    val newViewPositionY = (newZoomCenterInViewY - zoomCenter.y).toInt().coerceIn(0, newViewHeight - port.viewportComponent.height)

    port.viewPosition = Point(newViewPositionX, newViewPositionY)
  }
}
