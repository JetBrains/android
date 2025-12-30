/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.intellij.ui.components.JBViewport
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A viewport that scales the contained view relative to the center of the viewport. */
class CenterAnchoredViewport : JBViewport() {

  private var layoutUnderway = false
  private var resizingUnderway = false

  override fun setViewSize(newSize: Dimension) {
    val correctedViewPosition = computeCorrectedViewPosition(newSize)
    super.setViewSize(newSize)
    if (correctedViewPosition != viewPosition) {
      super.setViewPosition(correctedViewPosition)
    }
  }

  override fun setViewPosition(newViewPosition: Point) {
    if (view != null && !resizingUnderway) {
      val correctedViewPosition = computeCorrectedViewPosition(newViewPosition)
      super.setViewPosition(correctedViewPosition)
    }
  }

  override fun doLayout() {
    layoutUnderway = true
    try {
      super.doLayout()
    } finally {
      layoutUnderway = false
    }
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val view = view
    if (view != null && (width != this.width || height != this.height)) {
      resizingUnderway = true
      val viewPosition = viewPosition
      val viewPreferredSize = view.preferredSize
      val minX = (width - viewPreferredSize.width).coerceAtLeast(0) / 2
      val minY = (height - viewPreferredSize.height).coerceAtLeast(0) / 2
      // This formula is intended to eliminate systematic bias that would cause position drift after many small resizes.
      viewPosition.move((viewPosition.x + ((this.width - width + (viewPosition.x and 0x1)) shr 1)).coerceAtLeast(minX),
                        (viewPosition.y + ((this.height - height + (viewPosition.y and 0x1)) shr 1)).coerceAtLeast(minY))
      super.setViewPosition(viewPosition)
    }
    try {
      super.setBounds(x, y, width, height)
    }
    finally {
      resizingUnderway = false
    }
  }

  private fun computeCorrectedViewPosition(newViewSize: Dimension): Point {
    val viewPosition = viewPosition
    val view = view ?: return viewPosition
    if (scrollUnderway && !layoutUnderway || resizingUnderway) {
      return viewPosition
    }
    val oldViewWidth = view.width.toDouble()
    val oldViewHeight = view.height.toDouble()
    if (!view.isPreferredSizeSet || oldViewWidth == 0.0 || oldViewHeight == 0.0) {
      return viewPosition
    }
    val viewPreferredSize = view.preferredSize
    if (viewPreferredSize.width <= width && viewPreferredSize.height <= height) {
      return viewPosition
    }
    val naturalAspectRatio = viewPreferredSize.height.toDouble() / viewPreferredSize.width
    val oldContentOffsetX = (oldViewWidth - oldViewHeight / naturalAspectRatio).coerceAtLeast(0.0) / 2
    val oldContentOffsetY = (oldViewHeight - oldViewWidth * naturalAspectRatio).coerceAtLeast(0.0) / 2
    val newContentOffsetX = (newViewSize.width - newViewSize.height / naturalAspectRatio).coerceAtLeast(0.0) / 2
    val newContentOffsetY = (newViewSize.height - newViewSize.width * naturalAspectRatio).coerceAtLeast(0.0) / 2
    val scaleFactorX = newViewSize.width / oldViewWidth
    val scaleFactorY = newViewSize.height / oldViewHeight
    val scaleFactor = if (scaleFactorX * scaleFactorY < 1) min(scaleFactorX, scaleFactorY) else max(scaleFactorX, scaleFactorY)
    val viewportCenterX = width / 2.0
    val viewportCenterY = height / 2.0
    val oldFixedPointX = viewPosition.x + viewportCenterX - oldContentOffsetX
    val oldFixedPointY = viewPosition.y + viewportCenterY - oldContentOffsetY
    val newFixedPointX = oldFixedPointX * scaleFactor
    val newFixedPointY = oldFixedPointY * scaleFactor
    val x = if (newViewSize.width == view.width) viewPosition.x
            else (newFixedPointX - viewportCenterX + newContentOffsetX).roundToInt().coerceInLenient(0, newViewSize.width - width)
    val y = if (newViewSize.height == view.height) viewPosition.y
            else (newFixedPointY - viewportCenterY + newContentOffsetY).roundToInt().coerceInLenient(0, newViewSize.height - height)
    return Point(x, y)
  }

  private fun computeCorrectedViewPosition(newViewPosition: Point): Point {
    val view = view ?: return newViewPosition
    if ((scrollUnderway && !layoutUnderway) || !view.isPreferredSizeSet) {
      return newViewPosition
    }
    val viewPosition = viewPosition
    return Point(viewPosition.x.coerceInLenient(0, view.width - width), viewPosition.y.coerceInLenient(0, view.height - height))
  }
}

/** Similar to [coerceIn], but doesn't throw an exception when [max] < [min], in which case returns [min]. */
private fun Int.coerceInLenient(min: Int, max: Int): Int =
    if (max < min) min else coerceIn(min, max)
