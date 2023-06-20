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
import kotlin.math.sqrt

/**
 * This layout put the previews in the same group into the same rows and tries to not use the horizontal scrollbar in the surface.
 *
 * If there is only one visible preview, put it at the center of window.
 * If there are more than one visible previews, follows below logics to layout the previews:
 * - The first preview of a group is always at the start of a new row.
 * - The previews in the same group will be put in the same row.
 * - If there is no enough space to put the following previews, move them into the next row.
 * - If the space is not enough to put the first preview in the row, put it in that row, and the following previews should be put in the
 * next row. In this case, the horizontal scrollbar appears.
 *
 *
 * For example, assuming there are 3 groups, each have 3, 5, 2 previews:
 * - Window size is 800px
 * - Preview width is 200px
 * - No padding, margin, and frame delta between previews
 *
 * The layout will be:
 * ---------
 * |A A A  |
 * |B B B B|
 * |B      |
 * |C C    |
 * ---------
 *
 * [canvasTopPadding] is the top padding from the surface.
 * [previewFramePaddingProvider] is to provide the horizontal and vertical paddings of every "preview frame"s. The "preview frame" is a
 * preview with its toolbars.
 */
class GroupedGridSurfaceLayoutManager(@SwingCoordinate private val canvasTopPadding: Int,
                                      @SwingCoordinate private val previewFramePaddingProvider: (scale: Double) -> Int,
                                      private val transform: (Collection<PositionableContent>) -> List<List<PositionableContent>>)
  : SurfaceLayoutManager {

  override fun getPreferredSize(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate availableHeight: Int,
                                @SwingCoordinate dimension: Dimension?): Dimension {
    return getSize(content, PositionableContent::contentSize, { 1.0 }, availableWidth, dimension)
  }

  override fun getRequiredSize(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int,
                               @SwingCoordinate dimension: Dimension?) =
    getSize(content, PositionableContent::scaledContentSize, { scale }, availableWidth, dimension)

  /**
   * Get the total required size to layout the [content] with the given conditions.
   */
  private fun getSize(content: Collection<PositionableContent>,
                      sizeFunc: PositionableContent.() -> Dimension,
                      scaleFunc: PositionableContent.() -> Double,
                      availableWidth: Int,
                      dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()

    val groups = transform(content).map { group -> layoutGroup(group, scaleFunc, availableWidth) { sizeFunc().width } }

    val groupSizes = groups.map { group -> getGroupSize(group, sizeFunc, scaleFunc) }
    val requiredWidth = groupSizes.maxOf { it.width }
    val requiredHeight = groupSizes.sumOf { it.height }

    dim.setSize(requiredWidth, max(0, canvasTopPadding + requiredHeight))
    return dim
  }

  /**
   * Get the total required size of the [PositionableContent]s in grid layout.
   */
  private fun getGroupSize(grid: List<List<PositionableContent>>,
                           sizeFunc: PositionableContent.() -> Dimension,
                           scaleFunc: PositionableContent.() -> Double): Dimension {
    var groupRequiredWidth = 0
    var groupRequiredHeight = 0
    for (row in grid) {
      var rowX = 0

      var currentHeight = 0
      for (view in row) {
        val scale = scaleFunc(view)
        val margin = view.getMargin(scale)
        val framePadding = previewFramePaddingProvider(scale)
        rowX += framePadding + view.sizeFunc().width + margin.horizontal + framePadding
        currentHeight = max(currentHeight, framePadding + view.sizeFunc().height + margin.vertical + framePadding)
      }
      groupRequiredWidth = max(groupRequiredWidth, rowX)
      groupRequiredHeight += currentHeight
    }
    return Dimension(groupRequiredWidth, groupRequiredHeight)
  }

  @SurfaceScale
  override fun getFitIntoScale(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int): Double {
    // Use binary search to find the proper zoom-to-fit value.

    // Calculate the sum of the area of the original content sizes. This considers the margins and paddings of every content.
    val rawSizes = content.map {
      val contentSize = it.contentSize
      val margin = it.getMargin(1.0)
      val framePadding = previewFramePaddingProvider(1.0)
      Dimension(contentSize.width + margin.horizontal + framePadding * 2, contentSize.height + margin.vertical + framePadding * 2)
    }


    val upperBound = run {
      // Find the scale the total areas of contents equals to the available spaces.
      // This happens when the contents perfectly full-fill the available space.
      // It is not possible that the zoom-to-fit scale is larger than this value.
      val contentAreas = rawSizes.sumOf { it.width * it.height }
      val availableArea = availableWidth * (availableHeight - canvasTopPadding)
      // The zoom-to-fit value cannot be smaller than 1%.
      maxOf(0.01, sqrt (availableArea.toDouble() / contentAreas))
    }

    val lowerBound = run {
      // This scale can fit all the content in a single row or a single column, which is the worst case.
      // The zoom-to-fit scale should not be smaller than this value.
      val totalWidth = rawSizes.sumOf { it.width }
      val totalHeight = rawSizes.sumOf { it.height }
      // The zoom-to-fit value cannot be smaller than 1%.
      maxOf(0.01, minOf(availableWidth / totalWidth.toDouble(), (availableHeight - canvasTopPadding) / totalHeight.toDouble()))
    }

    if (upperBound <= lowerBound) {
      return lowerBound
    }

    return getMaxZoomToFitScale(content, lowerBound, upperBound, availableWidth, availableHeight, Dimension())
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
      val dim = getSize(content, { contentSize.scaleBy(max) }, { max }, width, cache)
      return if (dim.width <= width && dim.height <= height) max else min
    }
    val scale = (min + max) / 2
    val dim = getSize(content, { contentSize.scaleBy(scale) }, { scale }, width, cache)
    return if (dim.width <= width && dim.height <= height) {
      getMaxZoomToFitScale(content, scale, max, width, height, cache, depth + 1)
    }
    else {
      getMaxZoomToFitScale(content, min, scale, width, height, cache, depth + 1)
    }
  }

  /**
   * Arrange [PositionableContent]s into a 2-dimension list which represent a list of row of [PositionableContent].
   * The [widthFunc] is for getting the preferred widths of [PositionableContent]s when filling the horizontal spaces.
   */
  private fun layoutGroup(content: List<PositionableContent>,
                          scaleFunc: PositionableContent.() -> Double,
                          @SwingCoordinate availableWidth: Int,
                          @SwingCoordinate widthFunc: PositionableContent.() -> Int): List<List<PositionableContent>> {
    val visibleContent = content.filter { it.isVisible }
    if (visibleContent.isEmpty()) {
      return listOf(emptyList())
    }
    val gridList = mutableListOf<List<PositionableContent>>()

    val firstView = visibleContent.first()
    val firstPreviewFramePadding = previewFramePaddingProvider(scaleFunc(firstView))
    var nextX = firstPreviewFramePadding + firstView.widthFunc() + firstView.getMargin(firstView.scaleFunc()).horizontal + firstPreviewFramePadding

    var columnList = mutableListOf(firstView)
    for (view in visibleContent.drop(1)) {
      val framePadding = previewFramePaddingProvider(scaleFunc(view))
      val nextViewWidth = framePadding + view.widthFunc() + view.getMargin(view.scaleFunc()).horizontal + framePadding
      if (nextX + nextViewWidth > availableWidth) {
        nextX = nextViewWidth
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
      else {
        nextX += nextViewWidth
        columnList.add(view)
      }
    }
    gridList.add(columnList)
    return gridList
  }

  override fun measure(content: Collection<PositionableContent>,
                       availableWidth: Int,
                       availableHeight: Int,
                       keepPreviousPadding: Boolean): Map<PositionableContent, Point> {
    if (content.isEmpty()) {
      return emptyMap()
    }

    val visibleContents = content.filter { it.isVisible }
    if (visibleContents.size == 1) {
      val singleContent = content.single()
      // When there is only one visible preview, centralize it as a special case.
      val point = getSingleContentPosition(singleContent, availableWidth, availableHeight)

      return mapOf(singleContent to point) + content.filterNot { it.isVisible }.associateWith { Point(-1, -1) }
    }

    val groupedViews = transform(content)

    val startX: Int = 0
    val startY: Int = canvasTopPadding

    var nextGroupY = startY

    val positionMap = mutableMapOf<PositionableContent, Point>()

    for (group in groupedViews) {
      val grid = layoutGroup(group, { scale }, availableWidth) { scaledContentSize.width }
      var nextX = startX
      var nextY = nextGroupY
      var maxBottomInRow = 0
      for (row in grid) {
        for (view in row) {
          if (!view.isVisible) {
            continue
          }
          val framePadding = previewFramePaddingProvider(view.scale)
          positionMap[view] = getContentPosition(view, nextX + framePadding, nextY + framePadding)
          nextX += framePadding + view.scaledContentSize.width + view.margin.horizontal + framePadding
          maxBottomInRow = max(maxBottomInRow,
                               nextY + framePadding + view.margin.vertical + view.scaledContentSize.height + framePadding)
        }
        nextX = startX
        nextY = maxBottomInRow
      }

      nextGroupY = nextY
    }

    return positionMap + content.filterNot { it.isVisible }.associateWith { Point(-1, -1) }
  }

  @SwingCoordinate
  private fun getSingleContentPosition(content: PositionableContent,
                                       @SwingCoordinate availableWidth: Int,
                                       @SwingCoordinate availableHeight: Int): Point {
    val size = content.scaledContentSize
    val margin = content.margin
    val frameWidth = size.width + margin.horizontal
    val frameHeight = size.height + margin.vertical

    val framePadding = previewFramePaddingProvider(content.scale)

    // Try to centralize the content.
    val x = maxOf((availableWidth - frameWidth) / 2, framePadding)
    val y = maxOf((availableHeight - frameHeight) / 2, framePadding)
    return getContentPosition(content, x, y)
  }

  /**
   * Get the actual position should be set to the given [PositionableContent]
   */
  @SwingCoordinate
  private fun getContentPosition(content: PositionableContent, @SwingCoordinate previewX: Int, @SwingCoordinate previewY: Int): Point {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = previewX + margin.left
    val shiftedY = previewY + margin.top
    return Point(shiftedX, shiftedY)
  }
}
