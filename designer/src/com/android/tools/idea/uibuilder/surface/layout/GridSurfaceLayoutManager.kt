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
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The unit of scale when calculating the zoom-to-fit scale by calling [GridSurfaceLayoutManager.getFitIntoScale].
 * The recursion stops when the differences of two zoom-to-fit value is smaller than this value.
 */
private const val SCALE_UNIT = 0.01

/**
 * [SurfaceLayoutManager] that layouts [PositionableContent]s in grid style. It tries to fill the [PositionableContent]s horizontally then
 * vertically. When a row has no horizontal space for the next [PositionableContent], it fills the remaining [PositionableContent]s in the new
 * row, and so on.
 *
 * The [horizontalPadding] and [verticalPadding] are minimum gaps between [PositionableContent] and the boundaries of `NlDesignSurface`.
 * The [horizontalViewDelta] and [verticalViewDelta] are the gaps between different [PositionableContent]s.
 * The [centralizeContent] decides if the content should be placed at the center when the content is smaller then the surface size.
 */
open class GridSurfaceLayoutManager(@SwingCoordinate private val horizontalPadding: Int,
                                    @SwingCoordinate private val verticalPadding: Int,
                                    @SwingCoordinate private val horizontalViewDelta: Int,
                                    @SwingCoordinate private val verticalViewDelta: Int,
                                    private val centralizeContent: Boolean = true)
  : SurfaceLayoutManager {

  private var previousHorizontalPadding = 0
  private var previousVerticalPadding = 0

  private var fitIntoCache: Pair<FitIntoScaleData, Double>? = null

  override fun getPreferredSize(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate availableHeight: Int,
                                @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::contentSize, availableWidth, dimension)

  override fun getRequiredSize(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int,
                               @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::scaledContentSize, availableWidth, dimension)

  private fun getSize(content: Collection<PositionableContent>,
                      sizeFunc: PositionableContent.() -> Dimension,
                      availableWidth: Int,
                      dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()

    val grid = layoutGrid(content, availableWidth) { sizeFunc().width }
    var requiredWidth = 0
    var requiredHeight = 0

    for (row in grid) {
      var rowX = 0
      val rowY = requiredHeight
      var currentHeight = 0
      for (view in row) {
        rowX += view.sizeFunc().width + horizontalViewDelta + view.margin.horizontal
        currentHeight = max(currentHeight, rowY + verticalViewDelta + view.sizeFunc().height + view.margin.vertical)
      }
      requiredWidth = max(requiredWidth, max(rowX - horizontalViewDelta, 0))
      requiredHeight = currentHeight
    }

    dim.setSize(requiredWidth + 2 * horizontalPadding, max(0, requiredHeight - verticalViewDelta + 2 * verticalPadding))
    return dim
  }

  /**
   * Arrange [PositionableContent]s into a 2-dimension list which represent a list of row of [PositionableContent].
   * The [widthFunc] is for getting the preferred widths of [PositionableContent]s when filling the horizontal spaces.
   */
  protected open fun layoutGrid(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate widthFunc: PositionableContent.() -> Int): List<List<PositionableContent>> {
    val visibleContent = content.filter { it.isVisible }
    if (visibleContent.isEmpty()) {
      return listOf(emptyList())
    }
    val startX = horizontalPadding
    val gridList = mutableListOf<List<PositionableContent>>()

    val firstView = visibleContent.first()
    var nextX = startX + firstView.widthFunc() + firstView.margin.horizontal + horizontalViewDelta

    var columnList = mutableListOf(firstView)
    for (view in visibleContent.drop(1)) {
      // The full width is the view width + any horizontal margins
      val totalWidth = view.widthFunc() + view.margin.horizontal
      if (nextX + totalWidth > availableWidth) {
        nextX = horizontalPadding + totalWidth + horizontalViewDelta
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
      else {
        nextX += totalWidth + horizontalViewDelta
        columnList.add(view)
      }
    }
    gridList.add(columnList)
    return gridList
  }

  @SurfaceScale
  override fun getFitIntoScale(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int): Double {
    if (content.isEmpty()) {
      return 1.0
    }

    val previousData = fitIntoCache
    if (previousData != null) {
      val arguments = previousData.first
      if (arguments.content == content && arguments.availableWidth == availableWidth && arguments.availableHeight == availableHeight) {
        return previousData.second
      }
    }

    // Find the upper and lower bounds, then do binary search to find the best scaling size.

    // Calculate the sum of the area of the original content sizes. This considers the margins of every content.
    val rawSizes = content.map {
      val contentSize = it.contentSize
      val margin = it.margin
      Dimension(contentSize.width + margin.horizontal, contentSize.height + margin.vertical)
    }

    val upperBound = run {
      // Find the scale the total areas of contents equals to the available spaces.
      // This happens when the contents perfectly full-fill the available space.
      // It is not possible that the zoom-to-fit scale is larger than this value.
      val contentAreas = rawSizes.sumOf { it.width * it.height }
      val availableArea = (availableWidth - 2 * horizontalPadding) * (availableHeight - 2 * verticalPadding)
      sqrt(availableArea.toDouble() / contentAreas)
    }

    val lowerBound = run {
      // This scale can fit all the content in a single row or a single column, which is the worst case.
      // The zoom-to-fit scale should not be smaller than this value.
      val totalWidth = rawSizes.sumOf { it.width } + (content.size - 1) * horizontalViewDelta + 2 * horizontalPadding
      val totalHeight = rawSizes.sumOf { it.height } + (content.size - 1) * verticalViewDelta + 2 * verticalPadding
      maxOf(SCALE_UNIT, minOf(availableWidth.toDouble() / totalWidth, availableHeight.toDouble() / totalHeight))
    }

    // Do binary search to find the most proper scale.
    val scale = getMaxScale(content, lowerBound, upperBound, availableWidth, availableHeight, Dimension())
    fitIntoCache = FitIntoScaleData(content, availableWidth, availableHeight) to scale
    return scale
  }

  /**
   * Binary search to find the largest scale for [width] x [height] space.
   */
  @SurfaceScale
  private fun getMaxScale(content: Collection<PositionableContent>,
                          @SurfaceScale min: Double,
                          @SurfaceScale max: Double,
                          @SwingCoordinate width: Int,
                          @SwingCoordinate height: Int,
                          cache: Dimension): Double {
    if (max - min <= SCALE_UNIT) {
      // Last attempt.
      val dim = getSize(content, { contentSize.scaleBy(max) }, width, cache)
      return if (dim.width <= width && dim.height <= height) max else min
    }
    val scale = (min + max) / 2
    val dim = getSize(content, { contentSize.scaleBy(scale) }, width, cache)
    return if (dim.width <= width && dim.height <= height) {
      getMaxScale(content, scale, max, width, height, cache)
    }
    else {
      getMaxScale(content, min, scale, width, height, cache)
    }
  }

  override fun layout(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int,
                      keepPreviousPadding: Boolean) {
    if (content.isEmpty()) {
      return
    }

    val startX: Int
    val startY: Int
    if (!centralizeContent) {
      startX = horizontalPadding
      startY = verticalPadding
    }
    else if (keepPreviousPadding) {
      startX = previousHorizontalPadding
      startY = previousVerticalPadding
    }
    else {
      val dim = getRequiredSize(content, availableWidth, availableHeight, null)
      val paddingX = (availableWidth - dim.width) / 2
      val paddingY = (availableHeight - dim.height) / 2
      startX = max(paddingX, horizontalPadding)
      startY = max(paddingY, verticalPadding)
      previousHorizontalPadding = startX
      previousVerticalPadding = startY
    }

    val grid = layoutGrid(content, availableWidth) { scaledContentSize.width }

    var nextX = startX
    var nextY = startY
    var maxBottomInRow = 0
    for (row in grid) {
      for (view in row) {
        if (!view.isVisible) {
          continue
        }
        view.setLocation(nextX + view.margin.left, nextY)
        nextX += view.scaledContentSize.width + horizontalViewDelta + view.margin.horizontal
        maxBottomInRow = max(maxBottomInRow, nextY + view.margin.vertical + view.scaledContentSize.height)
      }
      nextX = startX
      nextY = maxBottomInRow + verticalViewDelta
    }

    content.filterNot { it.isVisible }.forEach { it.setLocation(-1, -1) }
  }

  /**
   * The data when calculating the [getFitIntoScale] value. This is used to avoid duplicated calculation.
   */
  private data class FitIntoScaleData(val content: Collection<PositionableContent>,
                                      val availableWidth: Int,
                                      val availableHeight: Int)
}
