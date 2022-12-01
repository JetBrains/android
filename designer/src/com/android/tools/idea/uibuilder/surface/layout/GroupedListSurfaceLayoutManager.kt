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
package com.android.tools.idea.uibuilder.surface.layout

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.SurfaceScale
import java.awt.Dimension
import kotlin.math.max

/**
 * This layout puts the previews in the same group together and list them vertically.
 * It centres every preview in the middle of the window.
 *
 * [canvasTopPadding] is the top padding from the surface.
 * [previewFramePaddingProvider] is to provide the horizontal and vertical paddings of every "preview frame". The "preview frame" is a
 * preview with its toolbars. The input value is the scale value of the current [PositionableContent].
 */
class GroupedListSurfaceLayoutManager(@SwingCoordinate private val canvasTopPadding: Int,
                                      @SwingCoordinate private val previewFramePaddingProvider: (scale: Double) -> Int,
                                      private val transform: (Collection<PositionableContent>) -> List<List<PositionableContent>>)
  : SurfaceLayoutManager {

  override fun getPreferredSize(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate availableHeight: Int,
                                @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::contentSize, { 1.0 }, dimension)

  override fun getRequiredSize(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int,
                               @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::scaledContentSize, { scale }, dimension)

  private fun getSize(content: Collection<PositionableContent>,
                      sizeFunc: PositionableContent.() -> Dimension,
                      scaleFunc: PositionableContent.() -> Double,
                      dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()

    val verticalList = transform(content).flatten()

    if (verticalList.isEmpty()) {
      dim.setSize(0, 0)
      return dim
    }

    var requiredWidth = 0
    var totalRequiredHeight = canvasTopPadding

    for (view in verticalList) {
      val viewSize = view.sizeFunc()
      val framePadding = previewFramePaddingProvider(scaleFunc(view))
      val viewWidth = framePadding + viewSize.width + view.margin.horizontal + framePadding
      val requiredHeight = framePadding + viewSize.height + view.margin.vertical + framePadding

      requiredWidth = maxOf(requiredWidth, viewWidth)
      totalRequiredHeight += requiredHeight
    }

    dim.setSize(requiredWidth, max(0, totalRequiredHeight))
    return dim
  }

  @SurfaceScale
  override fun getFitIntoScale(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int): Double {
    val contentSize = getPreferredSize(content, availableWidth, availableHeight, null)
    @SurfaceScale val scaleX: Double = if (contentSize.width == 0) 1.0 else availableWidth.toDouble() / contentSize.width
    @SurfaceScale val scaleY: Double = if (contentSize.height == 0) 1.0 else availableHeight.toDouble() / contentSize.height
    return minOf(scaleX, scaleY)
  }

  override fun layout(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int,
                      keepPreviousPadding: Boolean) {
    val verticalList = transform(content).flatten()
    if (verticalList.isEmpty()) {
      return
    }

    val widthMap =
      verticalList.associateWith {
        val framePadding = previewFramePaddingProvider(it.scale)
        framePadding + it.scaledContentSize.width + it.margin.horizontal + framePadding
      }
    val heightMap =
      verticalList.associateWith {
        val framePadding = previewFramePaddingProvider(it.scale)
        framePadding + it.scaledContentSize.height + it.margin.vertical + framePadding
      }

    val maxWidth = widthMap.values.maxOrNull() ?: 0
    val centerX: Int = maxOf(maxWidth, availableWidth) / 2

    val totalHeight = heightMap.values.sum()
    // centralizes the contents when total height is smaller than window height.
    val startY: Int = if (totalHeight + canvasTopPadding > availableHeight) canvasTopPadding else (availableHeight - totalHeight) / 2

    var nextY = startY
    for (view in verticalList) {
      val width = widthMap[view]!!
      val locationX = centerX - (width / 2)
      val framePadding = previewFramePaddingProvider(view.scale)
      setContentPosition(view, locationX + framePadding, nextY + framePadding)
      nextY += heightMap[view]!!
    }

    content.filterNot { it.isVisible }.forEach { setContentPosition(it, -1, -1) }
  }

  private fun setContentPosition(content: PositionableContent, x: Int, y: Int) {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = x + margin.left
    val shiftedY = y + margin.top
    content.setLocation(shiftedX, shiftedY)
  }
}
