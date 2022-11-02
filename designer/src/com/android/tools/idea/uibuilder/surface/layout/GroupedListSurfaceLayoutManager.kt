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
import java.awt.Dimension
import kotlin.math.max

/**
 * This layout puts the previews in the same group together and list them vertically.
 * It centres every preview in the middle of the window.
 *
 * [canvasTopPadding] is the top padding from the surface.
 * [previewFramePadding] is the horizontal and vertical paddings of every "preview frame". The "preview frame" is a preview with its
 * toolbars.
 */
class GroupedListSurfaceLayoutManager(@SwingCoordinate private val canvasTopPadding: Int,
                                      @SwingCoordinate private val previewFramePadding: Int,
                                      private val transform: (Collection<PositionableContent>) -> List<List<PositionableContent>>)
  : SurfaceLayoutManager {

  override fun getPreferredSize(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate availableHeight: Int,
                                @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::contentSize, dimension)

  override fun getRequiredSize(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int,
                               @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::scaledContentSize, dimension)

  private fun getSize(content: Collection<PositionableContent>,
                      sizeFunc: PositionableContent.() -> Dimension,
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
      val viewWidth = view.sizeFunc().width + view.margin.horizontal
      val requiredHeight = previewFramePadding + view.sizeFunc().height + view.margin.vertical + previewFramePadding

      requiredWidth = maxOf(requiredWidth, viewWidth)
      totalRequiredHeight += requiredHeight
    }

    dim.setSize(requiredWidth, max(0, totalRequiredHeight))
    return dim
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
      verticalList.associateWith { previewFramePadding + it.scaledContentSize.width + it.margin.horizontal + previewFramePadding }
    val heightMap =
      verticalList.associateWith { previewFramePadding + it.scaledContentSize.height + it.margin.vertical + previewFramePadding }

    val maxWidth = widthMap.values.maxOrNull() ?: 0
    val centerX: Int = maxOf(maxWidth, availableWidth) / 2

    val totalHeight = heightMap.values.sum()
    // centralizes the contents when total height is smaller than window height.
    val startY: Int = if (totalHeight + canvasTopPadding > availableHeight) canvasTopPadding else (availableHeight - totalHeight) / 2

    var nextY = startY
    for (view in verticalList) {
      val width = widthMap[view]!!
      val locationX = centerX - (width / 2)
      setContentPosition(view, locationX, nextY + previewFramePadding)
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
