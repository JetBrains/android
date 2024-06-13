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
package com.android.tools.idea.uibuilder.surface.layout

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.SurfaceScale
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.jetbrains.annotations.ApiStatus

/** The minumum height x width what should be available for the preview. */
private const val minumumPreviewSpacePx = 100 * 100

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

  /** Get the total required size to layout the [content] with the given conditions. */
  override fun getSize(
    content: Collection<PositionableContent>,
    sizeFunc: PositionableContent.() -> Dimension,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
    dimension: Dimension?,
  ): Dimension {
    val dim = dimension ?: Dimension()

    val groups =
      transform(content).map { group ->
        createLayoutGroup(group, scaleFunc, availableWidth) { sizeFunc().width }
      }

    val groupSizes = groups.map { group -> getGroupSize(group, sizeFunc, scaleFunc) }
    val requiredWidth = groupSizes.maxOfOrNull { it.width } ?: 0
    val requiredHeight = groupSizes.sumOf { it.height }

    dim.setSize(requiredWidth, max(0, padding.canvasTopPadding + requiredHeight))
    return dim
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
      val scale = scaleFunc(it)
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
        val scale = scaleFunc(view)
        val margin = view.getMargin(scale)
        rowX += view.sizeFunc().width + margin.horizontal + padding.previewRightPadding(scale, view)
        currentHeight =
          max(
            currentHeight,
            view.sizeFunc().height + margin.vertical + padding.previewBottomPadding(scale, view),
          )
      }

      groupRequiredWidth =
        max(groupRequiredWidth, rowX) +
          padding.canvasLeftPadding +
          if (layoutGroup.header != null) padding.groupLeftPadding else 0
      groupRequiredHeight += currentHeight
    }
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

    var maxNumberOfPreviews =
      (availableWidth - padding.canvasLeftPadding) * (availableHeight - padding.canvasTopPadding) /
        minumumPreviewSpacePx

    maxNumberOfPreviews = min(maxNumberOfPreviews, content.size)

    // Use binary search to find the proper zoom-to-fit value.

    // Calculate the sum of the area of the original content sizes. This considers the margins and
    // paddings of every content.
    val rawSizes =
      content.take(maxNumberOfPreviews).map {
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

    return getMaxZoomToFitScale(
      content.take(maxNumberOfPreviews),
      lowerBound,
      upperBound,
      availableWidth,
      availableHeight,
      Dimension(),
    )
  }

  /** Binary search to find the largest scale for [width] x [height] space. */
  @SurfaceScale
  private fun getMaxZoomToFitScale(
    content: Collection<PositionableContent>,
    @SurfaceScale min: Double,
    @SurfaceScale max: Double,
    @SwingCoordinate width: Int,
    @SwingCoordinate height: Int,
    cache: Dimension,
    depth: Int = 0,
  ): Double {
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
    return if (dim.height <= height) {
      getMaxZoomToFitScale(content, scale, max, width, height, cache, depth + 1)
    } else {
      getMaxZoomToFitScale(content, min, scale, width, height, cache, depth + 1)
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

    val groups = transform(content)
    var nextY = padding.canvasTopPadding
    var nextX = 0
    var maxYInRow = 0
    val positionMap = mutableMapOf<PositionableContent, Point>()

    groups.forEach { group ->
      val layoutGroup =
        createLayoutGroup(group, { scale }, availableWidth) { scaledContentSize.width }
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
