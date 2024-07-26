/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout.option

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.calculateHeightWithOffset
import com.android.tools.idea.common.layout.positionable.margin
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import com.android.tools.idea.common.model.scaleOf
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.layout.padding.OrganizationPadding
import com.android.tools.idea.uibuilder.layout.positionable.GridLayoutGroup
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.layout.positionable.content
import com.android.tools.idea.uibuilder.surface.layout.MAX_ITERATION_TIMES
import com.android.tools.idea.uibuilder.surface.layout.SCALE_UNIT
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.android.tools.idea.uibuilder.surface.layout.vertical
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import kotlin.math.sqrt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Experimental grid layout. All previews are organized in groups.
 *
 * TODO(b/321949200) Add tests
 */
@ApiStatus.Experimental
class GridLayoutManager(
  private val padding: OrganizationPadding,
  override val transform: (Collection<PositionableContent>) -> List<PositionableGroup>,
) : GroupedSurfaceLayoutManager(padding.previewPaddingProvider) {

  /** The list of all the [GridLayoutGroup]s applied in this manager. */
  private var cachedLayoutGroups: List<GridLayoutGroup>? = null

  /** When this value is true it should update [cachedLayoutGroups]. */
  private var currentAvailableWidth: Int? = null

  /** Get the total required size to layout the [content] with the given conditions. */
  override fun getSize(
    content: Collection<PositionableContent>,
    sizeFunc: PositionableContent.() -> Dimension,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
    dimension: Dimension?,
  ): Dimension {
    val groups = createLayoutGroups(transform(content), scaleFunc, availableWidth, sizeFunc)
    val groupSizes = groups.map { group -> getGroupSize(group, sizeFunc, scaleFunc) }
    val requiredWidth = groupSizes.maxOfOrNull { it.width } ?: 0
    val requiredHeight = groupSizes.sumOf { it.height }
    return Dimension(requiredWidth, max(0, padding.canvasTopPadding + requiredHeight))
  }

  /**
   * Creates a layout from the given [PositionableGroup]s considering the available width, it can
   * return a cached value if the content of the Groups hasn't changed.
   */
  @VisibleForTesting
  fun createLayoutGroups(
    groups: List<PositionableGroup>,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
    sizeFunc: PositionableContent.() -> Dimension,
  ): List<GridLayoutGroup> {
    val isSameWidth = currentAvailableWidth == availableWidth
    currentAvailableWidth = availableWidth

    val cachedGroups = cachedLayoutGroups
    // We skip creating a new layout group if the content hasn't changed, in this way we keep the
    // same size and layout group order when zooming in or when resizing the window.
    val canUseCachedGroups =
      groups.all { group: PositionableGroup ->
        cachedGroups
          ?.takeIf { StudioFlags.SCROLLABLE_ZOOM_ON_GRID.get() && isSameWidth }
          ?.any { it.content() == group.content } ?: false
      }
    return if (canUseCachedGroups && cachedGroups != null) {
      cachedGroups
    } else {
      val newGroup =
        groups.map { createLayoutGroup(it, scaleFunc, availableWidth) { sizeFunc().width } }
      cachedLayoutGroups = newGroup
      newGroup
    }
  }

  /**
   * Get the total required size of the [PositionableContent]s in grid layout. Includes horizontal
   * canvas padding.
   */
  private fun getGroupSize(
    layoutGroup: GridLayoutGroup,
    sizeFunc: PositionableContent.() -> Dimension,
    scaleFunc: PositionableContent.() -> Double,
  ): Dimension {
    var groupRequiredWidth = 0
    var groupRequiredHeight = 0
    layoutGroup.header?.let {
      val scale = it.scaleFunc()
      val margin = it.getMargin(scale)
      groupRequiredWidth =
        it.sizeFunc().width + margin.horizontal + padding.previewRightPadding(scale, it)
      groupRequiredHeight =
        it.sizeFunc().height + margin.vertical + padding.previewBottomPadding(scale, it)
    }

    for (row in layoutGroup.rows) {
      var rowX = 0
      var currentHeight = 0
      row.forEach { view ->
        val scale = view.scaleFunc()
        val margin = view.getMargin(scale)
        rowX += view.sizeFunc().width + margin.horizontal + padding.previewRightPadding(scale, view)
        currentHeight =
          max(
            currentHeight,
            view.calculateHeightWithOffset(view.sizeFunc().height, scale) +
              margin.vertical +
              padding.previewBottomPadding(scale, view),
          )
      }

      groupRequiredWidth = max(groupRequiredWidth, rowX)
      groupRequiredHeight += currentHeight
    }
    groupRequiredWidth +=
      padding.canvasLeftPadding + if (layoutGroup.header != null) padding.groupLeftPadding else 0
    return Dimension(groupRequiredWidth, groupRequiredHeight)
  }

  @SurfaceScale
  override fun getFitIntoScale(
    content: Collection<PositionableContent>,
    @SwingCoordinate availableWidth: Int,
    @SwingCoordinate availableHeight: Int,
  ): Double {
    if (content.isEmpty()) {
      // No content. Use 100% as zoom level
      return 1.0
    }

    // Use binary search to find the proper zoom-to-fit value.

    // Calculate the sum of the area of the original content sizes. This considers the margins and
    // paddings of every content.
    val rawSizes =
      content.map {
        val contentSize = it.contentSize
        val margin = it.getMargin(1.0)
        Dimension(
          contentSize.width + margin.horizontal + padding.previewRightPadding(1.0, it),
          contentSize.height + margin.vertical + padding.previewBottomPadding(1.0, it),
        )
      }

    val upperBound = run {
      // Find the scale the total areas of contents equals to the available spaces.
      // This happens when the contents perfectly full-fill the available space.
      // It is not possible that the zoom-to-fit scale is larger than this value.
      val contentAreas = rawSizes.sumOf { it.width * it.height }
      val availableArea =
        (availableWidth - padding.canvasLeftPadding) * (availableHeight - padding.canvasTopPadding)
      // The zoom-to-fit value cannot be smaller than 1%.
      maxOf(0.01, sqrt(availableArea.toDouble() / contentAreas))
    }

    val lowerBound = run {
      // This scale can fit all the content in a single row or a single column, which is the worst
      // case.
      // The zoom-to-fit scale should not be smaller than this value.
      val totalWidth = rawSizes.sumOf { it.width }
      val totalHeight = rawSizes.sumOf { it.height }
      // The zoom-to-fit value cannot be smaller than 1%.
      maxOf(
        0.01,
        minOf(
          (availableWidth - padding.canvasLeftPadding) / totalWidth.toDouble(),
          (availableHeight - padding.canvasTopPadding) / totalHeight.toDouble(),
        ),
      )
    }

    if (upperBound <= lowerBound) {
      return lowerBound
    }

    return getMaxZoomToFitScale(content, lowerBound, upperBound, availableWidth, availableHeight)
  }

  /** Binary search to find the largest scale for [width] x [height] space. */
  @SurfaceScale
  private fun getMaxZoomToFitScale(
    content: Collection<PositionableContent>,
    @SurfaceScale min: Double,
    @SurfaceScale max: Double,
    @SwingCoordinate width: Int,
    @SwingCoordinate height: Int,
    depth: Int = 0,
  ): Double {
    // We need to reset the cached layout to recreate a new one that fits the content.
    cachedLayoutGroups = null
    if (depth >= MAX_ITERATION_TIMES) {
      // because we want to show the content as wide as possible we return max even in case we reach
      // the max iteration, and we haven't found the perfect zoom to fit value.
      // It could be that the resulting zoom to fit is a bit higher than the available height. For a
      // better granularity we can increase MAX_ITERATION_TIMES
      return max
    }
    if (max - min <= SCALE_UNIT) {
      // max and min are minor than the unit scale, because we want to show the content as wide as
      // possible we return max
      // It could be that the resulting zoom to fit is a bit higher than the available height.
      return max
    }
    val scale = (min + max) / 2
    // We get the sizes of the content with the new scale applied.
    val dim = getSize(content, { contentSize.scaleOf(scale) }, { scale }, width, null)
    return if (dim.height <= height) {
      // We want the resulting content fitting into the height we try to lower the scale
      getMaxZoomToFitScale(content, scale, max, width, height, depth + 1)
    } else {
      // We want can increase the scale as the scaled dimension results on a lower height than the
      // available height
      getMaxZoomToFitScale(content, min, scale, width, height, depth + 1)
    }
  }

  /**
   * Arrange [PositionableGroup]s into a 2-dimension list which represent a list of row of
   * [PositionableContent]. The [widthFunc] is for getting the preferred widths of
   * [PositionableContent]s when filling the horizontal spaces.
   */
  private fun createLayoutGroup(
    group: PositionableGroup,
    scaleFunc: PositionableContent.() -> Double,
    @SwingCoordinate availableWidth: Int,
    @SwingCoordinate widthFunc: PositionableContent.() -> Int,
  ): GridLayoutGroup {
    if (group.content.isEmpty()) {
      return GridLayoutGroup(group.header, emptyList())
    }

    // Need to take into account canvas padding and group offset
    val width =
      availableWidth -
        padding.canvasLeftPadding -
        if (group.header != null) padding.groupLeftPadding else 0
    var nextX = 0
    val gridList = mutableListOf<List<PositionableContent>>()
    var columnList = mutableListOf<PositionableContent>()
    group.content.forEach { view ->
      val rightPadding = padding.previewRightPadding(scaleFunc(view), view)
      val nextViewWidth =
        view.widthFunc() + view.getMargin(view.scaleFunc()).horizontal + rightPadding
      if (nextX + nextViewWidth <= width || columnList.isEmpty()) {
        nextX += nextViewWidth
        columnList.add(view)
      } else {
        nextX = nextViewWidth
        gridList.add(columnList)
        columnList = mutableListOf(view)
      }
    }

    if (columnList.isNotEmpty()) gridList.add(columnList)
    return GridLayoutGroup(group.header, gridList)
  }

  override fun measure(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
    keepPreviousPadding: Boolean,
  ): Map<PositionableContent, Point> {
    if (content.isEmpty()) {
      return emptyMap()
    }

    if (content.size == 1 && content.first() !is HeaderPositionableContent) {
      val singleContent = content.single()
      // When there is only one visible preview, centralize it as a special case.
      val point = getSingleContentPosition(singleContent, availableWidth, availableHeight)

      return mapOf(singleContent to point)
    }

    val groups =
      createLayoutGroups(transform(content), { scale }, availableWidth, { scaledContentSize })
    var nextY = padding.canvasTopPadding
    var nextX = 0
    var maxYInRow = 0
    val positionMap = mutableMapOf<PositionableContent, Point>()

    groups.forEach { layoutGroup ->
      val groupOffsetX = if (layoutGroup.header != null) padding.groupLeftPadding else 0

      fun measure(view: PositionableContent) {
        positionMap[view] = getContentPosition(view, nextX, nextY)
        val framePaddingX = padding.previewRightPadding(view.scale, view)
        val framePaddingY = padding.previewBottomPadding(view.scale, view)
        nextX += view.scaledContentSize.width + view.margin.horizontal + framePaddingX
        maxYInRow =
          max(
            maxYInRow,
            nextY + view.margin.vertical + view.scaledContentSize.height + framePaddingY,
          )
      }
      layoutGroup.header?.let {
        nextX = padding.canvasLeftPadding
        measure(it)
        nextY = maxYInRow
      }
      layoutGroup.rows.forEach { row ->
        nextX = padding.canvasLeftPadding + groupOffsetX
        row.forEach { measure(it) }
        nextY = maxYInRow
      }
    }
    return positionMap
  }
}
