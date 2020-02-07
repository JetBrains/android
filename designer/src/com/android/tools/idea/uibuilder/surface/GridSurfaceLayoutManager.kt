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
import com.android.tools.idea.uibuilder.surface.layout.vertical
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.math.max

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

  override fun getPreferredSize(sceneViews: Collection<SceneView>,
                                availableWidth: Int,
                                availableHeight: Int,
                                dimension: Dimension?) =
    getSize(sceneViews, SceneView::getPreferredSize, availableWidth, availableHeight, dimension)

  override fun getRequiredSize(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?)
    = getSize(sceneViews, SceneView::getSize, availableWidth, availableHeight, dimension)

  private fun getSize(sceneViews: Collection<SceneView>,
                      sizeFunc: SceneView.() -> Dimension,
                      availableWidth: Int,
                      availableHeight: Int,
                      dimension: Dimension?)
    : Dimension {
    val dim = dimension ?: Dimension()

    val grid = convertToGrid(sceneViews, availableWidth) { sizeFunc().width }
    var requiredWidth = 0
    var requiredHeight = 0

    for (row in grid) {
      var rowX = 0
      val rowY = requiredHeight
      var currentHeight = 0
      for (view in row) {
        rowX += view.sizeFunc().width + horizontalViewDelta
        currentHeight = max(currentHeight, rowY + verticalViewDelta + view.sizeFunc().height + view.margin.vertical)
      }
      requiredWidth = max(requiredWidth, max(rowX - horizontalViewDelta, 0))
      requiredHeight = currentHeight
    }

    dim.setSize(requiredWidth, max(0, requiredHeight - verticalViewDelta))
    return dim
  }

  /**
   * Arrange [SceneView]s into a 2-dimension list which represent a list of row of [SceneView].
   * The [widthFunc] is for getting the preferred widths of [SceneView]s when filling the horizontal spaces.
   */
  private fun convertToGrid(sceneViews: Collection<SceneView>, availableWidth: Int, widthFunc: SceneView.() -> Int): List<List<SceneView>> {
    if (sceneViews.isEmpty()) {
      return listOf(emptyList())
    }
    val startX = horizontalPadding
    val gridList = mutableListOf<List<SceneView>>()

    val sortedSceneViews = sceneViews.sortByPosition()

    val firstView = sortedSceneViews[0]
    var nextX = startX + firstView.widthFunc() + horizontalViewDelta

    var columnList = mutableListOf(firstView)
    for (view in sortedSceneViews.drop(1)) {
      if (nextX + view.widthFunc() > availableWidth) {
        nextX = horizontalPadding + view.widthFunc() + horizontalViewDelta
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
      else {
        nextX += view.widthFunc() + horizontalViewDelta
        columnList.add(view)
      }
    }
    gridList.add(columnList)
    return gridList
  }

  override fun layout(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, keepPreviousPadding: Boolean) {
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

    val grid = convertToGrid(sceneViews, availableWidth) { size.width }

    var nextX = startX
    var nextY = startY
    var maxBottomInRow = 0
    for (row in grid) {
      for (view in row) {
        view.setLocation(nextX, nextY)
        nextX += view.size.width + horizontalViewDelta
        maxBottomInRow = max(maxBottomInRow, nextY + view.margin.vertical + view.size.height)
      }
      nextX = startX
      nextY = maxBottomInRow + verticalViewDelta
    }
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
