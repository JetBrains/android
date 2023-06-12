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
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.SurfaceScale
import java.awt.Dimension
import java.awt.Point
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
    // Use binary search to find the proper zoom-to-fit value.
    // Find the scale to put all the previews into the screen without any margin and padding.
    val totalRawHeight = content.sumOf { it.contentSize.height }
    val maxRawWidth = content.maxOf { it.contentSize.width }
    // The zoom-to-fit scale can not be larger than this scale, since it may need some margins and/or paddings.
    val upperBound = minOf(availableHeight.toDouble() / totalRawHeight, availableWidth.toDouble() / maxRawWidth)

    if (upperBound <= MINIMUM_SCALE) {
      return MINIMUM_SCALE
    }
    // binary search between MINIMUM_SCALE to upper bound.
    return getMaxZoomToFitScale(content, MINIMUM_SCALE, upperBound, availableWidth, availableHeight, Dimension())
  }

  /**
   * Binary search to find the largest scale for [width] x [height] space.
   */
  @SurfaceScale
  private fun getMaxZoomToFitScale(content: Collection<PositionableContent>,
                                   @SurfaceScale min: Double,
                                   @SurfaceScale max: Double,
                                   @SwingCoordinate width: Int,
                                   @SwingCoordinate height: Int,
                                   cache: Dimension,
                                   depth: Int = 0): Double {
    if (depth >= MAX_ITERATION_TIMES) {
      return min
    }
    if (max - min <= SCALE_UNIT) {
      // Last attempt.
      val dim = getSize(content, { contentSize.scaleBy(max) }, { max }, cache)
      return if (dim.width <= width && dim.height <= height) max else min
    }
    val scale = (min + max) / 2
    val dim = getSize(content, { contentSize.scaleBy(scale) }, { scale }, cache)
    return if (dim.width <= width && dim.height <= height) {
      getMaxZoomToFitScale(content, scale, max, width, height, cache, depth + 1)
    }
    else {
      getMaxZoomToFitScale(content, min, scale, width, height, cache, depth + 1)
    }
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

  override fun measure(content: Collection<PositionableContent>,
                       availableWidth: Int,
                       availableHeight: Int,
                       keepPreviousPadding: Boolean): Map<PositionableContent, Point> {
    val verticalList = transform(content).flatten()
    if (verticalList.isEmpty()) {
      return emptyMap()
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

    val positionMap = mutableMapOf<PositionableContent, Point>()

    // centralizes the contents when total height is smaller than window height.
    val startY: Int = if (totalHeight + canvasTopPadding > availableHeight) canvasTopPadding else (availableHeight - totalHeight) / 2

    var nextY = startY
    for (view in verticalList) {
      val width = widthMap[view]!!
      val locationX = centerX - (width / 2)
      val framePadding = previewFramePaddingProvider(view.scale)
      positionMap.setContentPosition(view, locationX + framePadding, nextY + framePadding)
      nextY += heightMap[view]!!
    }

    content.filterNot { it.isVisible }.forEach { positionMap.setContentPosition(it, -1, -1) }
    return positionMap
  }

  private fun MutableMap<PositionableContent, Point>.setContentPosition(content: PositionableContent, x: Int, y: Int) {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = x + margin.left
    val shiftedY = y + margin.top
    put(content, Point(shiftedX, shiftedY))
  }
}
