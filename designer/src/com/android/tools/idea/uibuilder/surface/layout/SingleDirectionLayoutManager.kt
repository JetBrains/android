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
 * A [SurfaceLayoutManager] which layouts all [PositionableContent]s vertically or horizontally depending on the given available size.
 *
 * The [horizontalPadding] and [verticalPadding] are the minimum gaps between [PositionableContent] and the edges of surface.
 * The [horizontalViewDelta] and [verticalViewDelta] are the fixed gap between different [PositionableContent]s.
 * Padding and view delta are always the same physical sizes on screen regardless the zoom level.
 *
 * [startBorderAlignment] allows to modify the start border aligment. See [Alignment].
 */
open class SingleDirectionLayoutManager(@SwingCoordinate private val horizontalPadding: Int,
                                        @SwingCoordinate private val verticalPadding: Int,
                                        @SwingCoordinate private val horizontalViewDelta: Int,
                                        @SwingCoordinate private val verticalViewDelta: Int,
                                        private val startBorderAlignment: Alignment = Alignment.CENTER)
  : SurfaceLayoutManager {

  /**
   * Determines the alignment for the start border of the element.
   * For a vertical alignment, [START] would mean left and [END] right. For horizontal, [START] means top and [END] means bottom.
   */
  enum class Alignment {
    START,
    CENTER,
    END
  }

  private var previousHorizontalPadding = 0
  private var previousVerticalPadding = 0

  override fun getPreferredSize(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate availableHeight: Int,
                                @SwingCoordinate dimension: Dimension?)
    : Dimension {
    return getSize(content, PositionableContent::contentSize, availableWidth, availableHeight, dimension)
  }

  override fun getRequiredSize(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int,
                               @SwingCoordinate dimension: Dimension?): Dimension {
    return getSize(content, PositionableContent::scaledContentSize, availableWidth, availableHeight, dimension)
  }

  private fun getSize(content: Collection<PositionableContent>,
                      sizeFunc: PositionableContent.() -> Dimension,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int,
                      @SwingCoordinate dimension: Dimension?): Dimension {
    val dim = dimension ?: Dimension()
    val vertical = isVertical(content, availableWidth, availableHeight)

    val width: Int
    val height: Int
    if (vertical) {
      width = content.maxOf { sizeFunc().width } ?: 0
      height = content.sumOf { margin.vertical + sizeFunc().height + verticalViewDelta } - verticalViewDelta
    }
    else {
      width = content.sumOf { margin.horizontal + sizeFunc().width + horizontalViewDelta } - horizontalViewDelta
      height = content.maxOf { sizeFunc().height } ?: 0
    }

    dim.setSize(max(0, width + 2 * horizontalPadding), max(0, height + 2 * verticalPadding))
    return dim
  }

  protected open fun isVertical(content: Collection<PositionableContent>,
                                @SwingCoordinate availableWidth: Int,
                                @SwingCoordinate availableHeight: Int): Boolean {
    if (content.isEmpty()) {
      return false
    }

    val primary = content.sortByPosition().first()
    return (availableHeight > 3 * availableWidth / 2) || primary.scaledContentSize.width > primary.scaledContentSize.height
  }

  @SurfaceScale
  override fun getFitIntoScale(content: Collection<PositionableContent>,
                               @SwingCoordinate availableWidth: Int,
                               @SwingCoordinate availableHeight: Int): Double {
    if (content.isEmpty()) {
      // No content. Use 100% as zoom level
      return 1.0
    }

    val vertical = isVertical(content, availableWidth, availableHeight)
    // We reserve the spaces for margins and paddings when calculate the zoom-to-fit scale. So there is always enough spaces for them.
    val margins = content.map { it.getMargin(1.0) }
    val reducedAvailableWidth: Int
    val reducedAvailableHeight: Int
    if (vertical) {
      reducedAvailableWidth = availableWidth - 2 * horizontalPadding - margins.sumOf { it.horizontal }
      reducedAvailableHeight = availableHeight - 2 * verticalPadding - (content.size - 1) * verticalViewDelta - margins.sumOf { it.vertical }
    }
    else {
      reducedAvailableWidth = availableWidth - 2 * horizontalPadding - (content.size - 1) * horizontalViewDelta - margins.sumOf { it.horizontal }
      reducedAvailableHeight = availableHeight - 2 * verticalPadding - margins.sumOf { it.vertical }
    }

    if (reducedAvailableWidth <= 0 || reducedAvailableHeight <= 0) {
      // There is not even enough space for paddings. In this case, force using (available size / 100% size) as the fit into scale.
      // This is an extreme case, be aware that this scale does not really fit the content.
      val preferredSize = getSize(content, PositionableContent::contentSize, availableWidth, availableHeight, null)
      return minOf(availableWidth.toDouble() / preferredSize.width, availableHeight.toDouble() / preferredSize.height)
    }

    // Get the raw width and height without paddings and view deltas.
    val listWidth: Int
    val listHeight: Int
    if (vertical) {
      listWidth = content.maxOf { contentSize.width }!!
      listHeight = content.sumOf { contentSize.height }
    }
    else {
      listWidth = content.sumOf { contentSize.width }
      listHeight = content.maxOf { contentSize.height }!!
    }

    return minOf( reducedAvailableWidth.toDouble() / listWidth, reducedAvailableHeight.toDouble() / listHeight)
  }

  override fun layout(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int,
                      keepPreviousPadding: Boolean) {
    if (content.isEmpty()) {
      return
    }

    val vertical = isVertical(content, availableWidth, availableHeight)

    val startX: Int
    val startY: Int
    if (keepPreviousPadding) {
      startX = previousHorizontalPadding
      startY = previousVerticalPadding
    } else {
      val requiredSize = getRequiredSize(content, availableWidth, availableHeight, null)
      val requiredContentWidth = requiredSize.width - 2 * horizontalPadding
      val requiredContentHeight = requiredSize.height - 2 * verticalPadding
      startX = max((availableWidth - requiredContentWidth) / 2, horizontalPadding)
      startY = max((availableHeight - requiredContentHeight) / 2, verticalPadding)
      previousHorizontalPadding = startX
      previousVerticalPadding = startY
    }

    if (vertical) {
      var nextY = startY
      for (sceneView in content) {
        nextY += sceneView.margin.top
        val xPosition = max(startX, when (startBorderAlignment) {
          Alignment.START -> startX
          Alignment.END -> availableWidth - sceneView.scaledContentSize.width
          Alignment.CENTER -> (availableWidth - sceneView.scaledContentSize.width) / 2
        })
        sceneView.setLocation(xPosition, nextY)
        nextY += sceneView.scaledContentSize.height + sceneView.margin.bottom + verticalViewDelta
      }
    }
    else {
      var nextX = startX
      for (sceneView in content) {
        // Centered in the horizontal centerline
        val yPosition = max(startY, when (startBorderAlignment) {
          Alignment.START -> startY
          Alignment.END -> availableHeight - (sceneView.scaledContentSize.height + sceneView.margin.vertical)
          Alignment.CENTER -> availableHeight / 2 - (sceneView.scaledContentSize.height + sceneView.margin.vertical) / 2
        })
        sceneView.setLocation(nextX, yPosition)
        nextX += sceneView.scaledContentSize.width + horizontalViewDelta
      }
    }
  }
}

/**
 * [SingleDirectionLayoutManager] that forces the content to always be vertical.
 */
class VerticalOnlyLayoutManager(@SwingCoordinate horizontalPadding: Int,
                                @SwingCoordinate verticalPadding: Int,
                                @SwingCoordinate horizontalViewDelta: Int,
                                @SwingCoordinate verticalViewDelta: Int,
                                val startBorderAlignment: Alignment) : SingleDirectionLayoutManager(
  horizontalPadding, verticalPadding, horizontalViewDelta, verticalViewDelta, startBorderAlignment) {
  override fun isVertical(content: Collection<PositionableContent>,
                          @SwingCoordinate availableWidth: Int,
                          @SwingCoordinate availableHeight: Int): Boolean = true
}

// Helper functions to improve readability
private fun Collection<PositionableContent>.sumOf(mapFunc: PositionableContent.() -> Int) = map(mapFunc).sum()
private fun Collection<PositionableContent>.maxOf(mapFunc: PositionableContent.() -> Int) = map(mapFunc).maxOrNull()
