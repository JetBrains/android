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
 * This layout put the previews in the same group into the same rows and tries to not use the horizontal scrollbar in the surface.
 *
 * It follows below logics to layout the previews:
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
 * [previewFramePadding] is the horizontal and vertical paddings of every "preview frame"s. The "preview frame" is a preview with its
 * toolbars.
 */
class GroupedGridSurfaceLayoutManager(@SwingCoordinate private val canvasTopPadding: Int,
                                      @SwingCoordinate private val previewFramePadding: Int,
                                      private val transform: (Collection<PositionableContent>) -> List<List<PositionableContent>>)
  : SurfaceLayoutManager {

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

    val groups = transform(content).map { group -> layoutGroup(group, availableWidth) { sizeFunc().width } }

    var requiredWidth = 0
    var totalRequiredHeight = 0

    for (group in groups) {
      var groupRequiredHeight = 0
      for (row in group) {
        var rowX = 0
        val rowY = 0
        var currentHeight = 0
        for (view in row) {
          rowX += previewFramePadding + view.sizeFunc().width + view.margin.horizontal + previewFramePadding
          currentHeight = max(currentHeight,
                              rowY + previewFramePadding + view.sizeFunc().height + view.margin.vertical + previewFramePadding)
        }
        requiredWidth = max(requiredWidth, max(rowX - previewFramePadding, 0))
        groupRequiredHeight += currentHeight
      }
      totalRequiredHeight += groupRequiredHeight
    }

    dim.setSize(requiredWidth, max(0, canvasTopPadding + totalRequiredHeight - previewFramePadding))
    return dim
  }

  /**
   * Arrange [PositionableContent]s into a 2-dimension list which represent a list of row of [PositionableContent].
   * The [widthFunc] is for getting the preferred widths of [PositionableContent]s when filling the horizontal spaces.
   */
  private fun layoutGroup(content: List<PositionableContent>,
                          @SwingCoordinate availableWidth: Int,
                          @SwingCoordinate widthFunc: PositionableContent.() -> Int): List<List<PositionableContent>> {
    val visibleContent = content.filter { it.isVisible }
    if (visibleContent.isEmpty()) {
      return listOf(emptyList())
    }
    val gridList = mutableListOf<List<PositionableContent>>()

    val firstView = visibleContent.first()
    var nextX = previewFramePadding + firstView.widthFunc() + firstView.margin.horizontal + previewFramePadding

    var columnList = mutableListOf(firstView)
    for (view in visibleContent.drop(1)) {
      // The width without right padding is: left frame padding + view width + any horizontal margins.
      val totalWidth = previewFramePadding + view.widthFunc() + view.margin.horizontal
      if (nextX + totalWidth > availableWidth) {
        nextX = totalWidth + previewFramePadding // Append the right padding.
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
      else {
        nextX += totalWidth + previewFramePadding  // Append the right padding.
        columnList.add(view)
      }
    }
    gridList.add(columnList)
    return gridList
  }

  override fun layout(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int,
                      keepPreviousPadding: Boolean) {
    if (content.isEmpty()) {
      return
    }

    val groupedViews = transform(content)

    val startX: Int = 0
    val startY: Int = canvasTopPadding

    var nextGroupY = startY

    for (group in groupedViews) {
      val grid = layoutGroup(group, availableWidth) { scaledContentSize.width }
      var nextX = startX
      var nextY = nextGroupY
      var maxBottomInRow = 0
      for (row in grid) {
        for (view in row) {
          if (!view.isVisible) {
            continue
          }
          setContentPosition(view, nextX + view.margin.left + previewFramePadding, nextY + previewFramePadding)
          nextX += previewFramePadding + view.scaledContentSize.width + view.margin.horizontal + previewFramePadding
          maxBottomInRow = max(maxBottomInRow,
                               nextY + previewFramePadding + view.margin.vertical + view.scaledContentSize.height + previewFramePadding)
        }
        nextX = startX
        nextY = maxBottomInRow
      }

      nextGroupY = nextY
    }

    content.filterNot { it.isVisible }.forEach { it.setLocation(-1, -1) }
  }

  private fun setContentPosition(content: PositionableContent, x: Int, y: Int) {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = x + margin.left
    val shiftedY = y + margin.top
    content.setLocation(shiftedX, shiftedY)
  }
}
