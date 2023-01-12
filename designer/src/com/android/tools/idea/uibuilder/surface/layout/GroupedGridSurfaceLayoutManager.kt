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
import java.awt.Point
import kotlin.math.max

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

  private fun getSize(content: Collection<PositionableContent>,
                      sizeFunc: PositionableContent.() -> Dimension,
                      scaleFunc: PositionableContent.() -> Double,
                      availableWidth: Int,
                      dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()

    val groups = transform(content).map { group -> layoutGroup(group, scaleFunc, availableWidth) { sizeFunc().width } }

    var requiredWidth = 0
    var totalRequiredHeight = 0

    for (group in groups) {
      var groupRequiredHeight = 0
      for (row in group) {
        var rowX = 0
        val rowY = 0
        var currentHeight = 0
        for (view in row) {
          val scale = scaleFunc(view)
          val margin = view.getMargin(scale)
          val framePadding = previewFramePaddingProvider(scale)
          rowX += framePadding + view.sizeFunc().width + margin.horizontal + framePadding
          currentHeight = max(currentHeight,
                              rowY + framePadding + view.sizeFunc().height + margin.vertical + framePadding)
        }
        val lastFramePadding = row.lastOrNull()?.let { previewFramePaddingProvider(scaleFunc(it)) } ?: 0
        requiredWidth = max(requiredWidth, max(rowX - lastFramePadding, 0))
        groupRequiredHeight += currentHeight
      }
      totalRequiredHeight += groupRequiredHeight
    }

    dim.setSize(requiredWidth, max(0, canvasTopPadding + totalRequiredHeight))
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
      // The width without right padding is: left frame padding + view width + any horizontal margins.
      val totalWidth = framePadding + view.widthFunc() + view.getMargin(view.scaleFunc()).horizontal
      if (nextX + totalWidth > availableWidth) {
        nextX = totalWidth + framePadding // Append the right padding.
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
      else {
        nextX += totalWidth + framePadding  // Append the right padding.
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
          positionMap[view] = getContentPosition(view, nextX + view.margin.left + framePadding, nextY + framePadding)
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
