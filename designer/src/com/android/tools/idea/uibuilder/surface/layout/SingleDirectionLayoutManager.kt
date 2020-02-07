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
import com.android.tools.idea.common.surface.SceneView
import java.awt.Dimension
import kotlin.math.max

/**
 * A [SurfaceLayoutManager] which layouts all [SceneView]s vertically or horizontally depending on the given available size.
 *
 * The [horizontalPadding] and [verticalPadding] are the minimum gaps between [SceneView] and the edges of surface.
 * The [horizontalViewDelta] and [verticalViewDelta] are the fixed gap between different [SceneView]s.
 * Padding and view delta are always the same physical sizes on screen regardless the zoom level.
 */
class SingleDirectionLayoutManager(@SwingCoordinate private val horizontalPadding: Int,
                                   @SwingCoordinate private val verticalPadding: Int,
                                   @SwingCoordinate private val horizontalViewDelta: Int,
                                   @SwingCoordinate private val verticalViewDelta: Int)
  : SurfaceLayoutManager {

  private var previousHorizontalPadding = 0
  private var previousVerticalPadding = 0

  override fun getPreferredSize(sceneViews: Collection<SceneView>,
                                availableWidth: Int,
                                availableHeight: Int,
                                dimension: Dimension?)
    : Dimension {
    val dim = dimension ?: Dimension()

    val vertical = isVertical(sceneViews, availableWidth, availableHeight)

    val preferredWidth: Int
    val preferredHeight: Int
    if (vertical) {
      preferredWidth = sceneViews.maxOf { contentSize.width } ?: 0
      preferredHeight = sceneViews.sumOf { margin.vertical + contentSize.height + verticalViewDelta } - verticalViewDelta
    }
    else {
      preferredWidth = sceneViews.sumOf { contentSize.width + horizontalViewDelta } - horizontalViewDelta
      preferredHeight = sceneViews.maxOf { contentSize.height } ?: 0
    }

    val width = max(0, preferredWidth)
    val height = max(0, preferredHeight)
    dim.setSize(width, height)
    return dim
  }

  override fun getRequiredSize(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()

    val requiredWidth: Int
    val requiredHeight: Int
    if (isVertical(sceneViews, availableWidth, availableHeight)) {
      requiredWidth = sceneViews.maxOf { scaledContentSize.width } ?: 0
      requiredHeight = sceneViews.sumOf { margin.vertical + scaledContentSize.height + verticalViewDelta } - verticalViewDelta
    }
    else {
      requiredWidth = sceneViews.sumOf { scaledContentSize.width + horizontalViewDelta } - horizontalViewDelta
      requiredHeight = sceneViews.maxOf { scaledContentSize.height } ?: 0
    }

    val width = max(0, requiredWidth)
    val height = max(0, requiredHeight)
    dim.setSize(width, height)
    return dim
  }

  private fun isVertical(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int): Boolean {
    if (sceneViews.isEmpty()) {
      return false
    }

    val primary = sceneViews.sortByPosition().first()
    return (availableHeight > 3 * availableWidth / 2) || primary.scaledContentSize.width > primary.scaledContentSize.height
  }

  override fun layout(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, keepPreviousPadding: Boolean) {
    if (sceneViews.isEmpty()) {
      return
    }

    val vertical = isVertical(sceneViews, availableWidth, availableHeight)

    val startX: Int
    val startY: Int
    if (keepPreviousPadding) {
      startX = previousHorizontalPadding
      startY = previousVerticalPadding
    } else {
      val requiredSize = getRequiredSize(sceneViews, availableWidth, availableHeight, null)
      val requiredWidth = requiredSize.width
      val requiredHeight = requiredSize.height
      startX = max((availableWidth - requiredWidth) / 2, horizontalPadding)
      startY = max((availableHeight - requiredHeight) / 2, verticalPadding)
      previousHorizontalPadding = startX
      previousVerticalPadding = startY
    }

    if (vertical) {
      var nextY = startY
      for (sceneView in sceneViews) {
        nextY += sceneView.margin.top
        sceneView.setLocation(startX, nextY)
        nextY += sceneView.scaledContentSize.height + sceneView.margin.bottom + verticalViewDelta
      }
    }
    else {
      var nextX = startX
      for (sceneView in sceneViews) {
        sceneView.setLocation(nextX, startY)
        nextX += sceneView.scaledContentSize.width + horizontalViewDelta
      }
    }
  }
}

// Helper functions to improve readability
private fun Collection<SceneView>.sumOf(mapFunc: SceneView.() -> Int) = map(mapFunc).sum()
private fun Collection<SceneView>.maxOf(mapFunc: SceneView.() -> Int) = map(mapFunc).max()
