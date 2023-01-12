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

  @SurfaceScale
  override fun getFitIntoScale(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int): Double {
    if (content.isEmpty()) {
      // No content. Use 100% as zoom level
      return 1.0
    }
    // We reserve the spaces for margins paddings when calculate the zoom-to-fit scale. So there is always enough spaces for them.

    val margins = content.map { it.getMargin(1.0) }
    // Reserve the canvas and frame paddings, so the scaled content must be able to fit into the area.
    val reducedAvailableWidth = availableWidth - 2 * previewFramePaddingProvider(1.0) - margins.sumOf { it.horizontal }
    val reducedAvailableHeight =
      availableHeight - canvasTopPadding - previewFramePaddingProvider(1.0) * 2 * content.size - margins.sumOf { it.vertical }

    if (reducedAvailableWidth <= 0 || reducedAvailableHeight <= 0) {
      // There is not even enough space for paddings. In this case, force using (available size / 100% size) as the fit into scale.
      // This is an extreme case, be aware that this scale does not really fit the content.
      val preferredSize = getSize(content, PositionableContent::contentSize, { 1.0 }, null)
      return minOf(availableWidth.toDouble() / preferredSize.width, availableHeight.toDouble() / preferredSize.height)
    }

    // The total size of contents when zoom level is 100%. The padding space is reserved, just calculate the raw size.
    val sizes = content.map { it.contentSize }
    val listWidth = sizes.maxOf { it.width }
    val listHeight = sizes.sumOf { it.height }

    return minOf( reducedAvailableWidth.toDouble() / listWidth, reducedAvailableHeight.toDouble() / listHeight)
  }

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
      val scale = view.scaleFunc()
      val margin = view.getMargin(scale)
      val viewSize = view.sizeFunc()
      val framePadding = previewFramePaddingProvider(scale)
      val viewWidth = framePadding + viewSize.width + margin.horizontal + framePadding
      val requiredHeight = framePadding + viewSize.height + margin.vertical + framePadding

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
