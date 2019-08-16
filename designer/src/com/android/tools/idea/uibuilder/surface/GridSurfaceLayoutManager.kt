/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.surface.SceneView
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.math.max

/**
 * We have to use scaled size to measure the (row, column) position for [SceneView]s.
 * The actual preview image size is too large for layout measuring, we use (image size)/RATIO
 * instead.
 * <p>
 * The [RATIO] here is a fine-tuned value as a temporarily solution. For better solution we may
 * want to handle the zoom-level in [SurfaceLayoutManager] instead of [NlDesignSurface].
 */
private const val RATIO: Double = 4.5

/**
 * Width used to fill the row when measuring the size and position of grid.
 */
private val SceneView.gridWidth: Int
  get() = (preferredSize.width / RATIO).toInt()

/**
 * [SurfaceLayoutManager] that layouts [SceneView]s in grid style. It tries to fill the [SceneView]s horizontally then vertically.
 * When a row has no horizontal space for the next [SceneView], it fills the remaining [SceneView]s in the new row, and so on.
 *
 * The [horizontalPadding] and [verticalPadding] are minimum gaps between [SceneView] and the boundaries of [NlDesignSurface].
 * The [horizontalViewDelta] and [verticalViewDelta] are the gaps between different [SceneView]s.
 */
class GridSurfaceLayoutManager(private val horizontalPadding: Int,
                               private val verticalPadding: Int,
                               private val horizontalViewDelta: Int,
                               private val verticalViewDelta: Int)
  : SurfaceLayoutManager {

  private var previousHorizontalPadding = 0
  private var previousVerticalPadding = 0

  override fun getPreferredSize(sceneViews: List<SceneView>,
                                availableWidth: Int,
                                availableHeight: Int,
                                dimension: Dimension?) =
    getSize(sceneViews, SceneView::getPreferredSize, availableWidth, availableHeight, dimension)

  override fun getRequiredSize(sceneViews: List<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?) =
    getSize(sceneViews, SceneView::getSize, availableWidth, availableHeight, dimension)

  private fun getSize(sceneViews: List<SceneView>,
                      sizeFunc: SceneView.() -> Dimension,
                      availableWidth: Int,
                      availableHeight: Int,
                      dimension: Dimension?)
    : Dimension {
    val dim = dimension ?: Dimension()

    val grid = convertToGrid(sceneViews, availableWidth, availableHeight)
    var requiredWidth = 0
    var requiredHeight = 0

    for (row in grid) {
      var rowX = 0
      val rowY = requiredHeight
      var currentHeight = 0
      for (view in row) {
        rowX += view.sizeFunc().width + horizontalViewDelta
        currentHeight = max(currentHeight, rowY + verticalViewDelta + view.sizeFunc().height)
      }
      requiredWidth = max(requiredWidth, rowX)
      requiredHeight = currentHeight
    }

    dim.setSize(requiredWidth, requiredHeight)
    return dim
  }

  /**
   * Arrange [SceneView]s into a 2-dimension list which represent a list of row of [SceneView]
   */
  private fun convertToGrid(sceneViews: List<SceneView>, availableWidth: Int, availableHeight: Int): List<List<SceneView>> {
    if (sceneViews.isEmpty()) {
      return listOf(emptyList())
    }
    val startX = horizontalPadding
    val gridList = mutableListOf<List<SceneView>>()

    val iterator = sceneViews.iterator()

    val firstView = iterator.next()
    var nextX = startX + firstView.gridWidth + horizontalViewDelta

    var columnList = mutableListOf(firstView)
    for (view in iterator) {
      if (nextX + view.gridWidth > availableWidth) {
        nextX = horizontalPadding + view.gridWidth + horizontalViewDelta
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
      else {
        nextX += view.gridWidth + horizontalViewDelta
        columnList.add(view)
      }
    }
    gridList.add(columnList)
    return gridList
  }

  override fun layout(sceneViews: List<SceneView>, availableWidth: Int, availableHeight: Int, keepPreviousPadding: Boolean) {
    if (sceneViews.isEmpty()) {
      return
    }

    val startX: Int
    val startY: Int
    if (keepPreviousPadding) {
      startX = previousHorizontalPadding
      startY = previousVerticalPadding
    }
    else {
      val dim = getRequiredSize(sceneViews, availableWidth, availableHeight, null)
      val paddingX = (availableWidth - dim.width) / 2
      val paddingY = (availableHeight - dim.height) / 2
      startX = max(paddingX, horizontalPadding)
      startY = max(paddingY, verticalPadding)
      previousHorizontalPadding = startX
      previousVerticalPadding = startY
    }

    val grid = convertToGrid(sceneViews, availableWidth, availableHeight)

    var nextX = startX
    var nextY = startY
    var maxBottomInRow = 0
    for (row in grid) {
      for (view in row) {
        view.setLocation(nextX, nextY)
        nextX += view.size.width + horizontalViewDelta
        maxBottomInRow = max(maxBottomInRow, nextY + view.nameLabelHeight + view.size.height)
      }
      nextX = startX
      nextY = maxBottomInRow + verticalViewDelta
    }
  }

  override fun getRenderableBoundsForInvisibleComponents(targetSceneView: SceneView,
                                                         sceneViews: List<SceneView>,
                                                         availableWidth: Int,
                                                         availableHeight: Int,
                                                         surfaceRect: Rectangle,
                                                         retRect: Rectangle?)
    : Rectangle {
    val rectangle = retRect ?: Rectangle()

    if (sceneViews.size == 1) {
      rectangle.bounds = surfaceRect
      return rectangle
    }

    val index = sceneViews.indexOf(targetSceneView)
    assert(index != -1)

    val grid = convertToGrid(sceneViews, availableWidth, availableHeight)

    // Calculate which row/column the given SceneView located.
    var row = 0
    var column = index
    while (column >= grid[row].size) {
      column -= grid[row].size
      row += 1
    }

    val aboveSceneViews = grid.getOrNull(row - 1)
    val topBound = if (aboveSceneViews == null) {
      // The target SceneView is at first row, there is no SceneView above it.
      surfaceRect.top
    }
    else {
      // Find the most bottom point in previous row, and get the middle point between it and targetSceneView
      (aboveSceneViews.map { it.bottom }.max()!! + targetSceneView.top) / 2
    }

    val belowSceneViews = grid.getOrNull(row + 1)
    val bottomBound = if (belowSceneViews == null) {
      // The target SceneView is at last row, there is no SceneView below it.
      surfaceRect.bottom
    }
    else {
      // Find the most top point in next row, and get the middle point between it and targetSceneView
      (belowSceneViews.map { it.top }.max()!! + targetSceneView.bottom) / 2
    }

    val leftSceneView = grid[row].getOrNull(column - 1)
    val leftBound = if (leftSceneView == null) {
      // The target SceneView is the first SceneView in the row, there is no SceneView on its left side.
      surfaceRect.left
    }
    else {
      // Find the middle point between left SceneView and targetSceneView
      (leftSceneView.right + targetSceneView.left) / 2
    }

    val rightSceneView = grid[row].getOrNull(column + 1)

    val rightBound = if (rightSceneView == null) {
      // The target SceneView is the last SceneView in the row, there is no SceneView on its right side.
      surfaceRect.right
    }
    else {
      // Find the middle point between right SceneView and targetSceneView
      (rightSceneView.left + targetSceneView.right) / 2
    }

    rectangle.setLocation(leftBound, topBound)
    rectangle.setSize(rightBound - leftBound, bottomBound - topBound)
    rectangle.setFrame(leftBound.toDouble(), topBound.toDouble(), rightBound.toDouble(), bottomBound.toDouble())
    return rectangle
  }
}

// Helper functions to improve readability
private val Rectangle.left: Int
  get() = x
private val Rectangle.top: Int
  get() = y
private val Rectangle.right: Int
  get() = x + width
private val Rectangle.bottom: Int
  get() = y + height

private val SceneView.left: Int
  get() = x
private val SceneView.top: Int
  get() = y
private val SceneView.right: Int
  get() = x + size.width
private val SceneView.bottom: Int
  get() = y + size.height
