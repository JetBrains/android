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
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.calculateHeightWithOffset
import com.android.tools.idea.common.layout.positionable.margin
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import com.android.tools.idea.common.model.scaleOf
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.SCROLLABLE_ZOOM_ON_GRID
import com.android.tools.idea.uibuilder.layout.option.GroupedSurfaceLayoutManager
import com.android.tools.idea.uibuilder.layout.padding.GroupPadding
import com.android.tools.idea.uibuilder.layout.positionable.GridLayoutGroup
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.layout.positionable.content
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import kotlin.math.sqrt

/** The minumum height x width what should be available for the preview. */
private const val minumumPreviewSpacePx = 100 * 100

/**
 * This layout put the previews in the same group into the same rows and tries to not use the
 * horizontal scrollbar in the surface.
 *
 * If there is only one visible preview, put it at the center of window. If there are more than one
 * visible previews, follows below logics to layout the previews:
 * - The first preview of a group is always at the start of a new row.
 * - The previews in the same group will be put in the same row.
 * - If there is no enough space to put the following previews, move them into the next row.
 * - If the space is not enough to put the first preview in the row, put it in that row, and the
 *   following previews should be put in the next row. In this case, the horizontal scrollbar
 *   appears.
 *
 * For example, assuming there are 3 groups, each have 3, 5, 2 previews:
 * - Window size is 800px
 * - Preview width is 200px
 * - No padding, margin, and frame delta between previews
 *
 * The layout will be:
 * ---------
 * |A A A | |B B B B| |B | |C C |
 * ---------
 * @param padding layout paddings
 */
open class GroupedGridSurfaceLayoutManager(
  private val padding: GroupPadding,
  override val transform: (Collection<PositionableContent>) -> List<PositionableGroup>,
) : GroupedSurfaceLayoutManager(padding.previewPaddingProvider) {

  private var currentAvailableWidth: Int? = null

  private var currentLayoutGroup: GridLayoutGroup? = null

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

    dim.setSize(
      max(0, padding.canvasLeftPadding + requiredWidth),
      max(0, padding.canvasTopPadding + requiredHeight),
    )
    return dim
  }

  /** Get the total required size of the [PositionableContent]s in grid layout. */
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
      val framePadding = padding.previewPaddingProvider(scale)
      groupRequiredWidth = 2 * framePadding + it.sizeFunc().width + margin.horizontal
      groupRequiredHeight = 2 * framePadding + it.calculateHeightWithOffset(it.sizeFunc().height, scale)  + margin.vertical
    }

    for (row in layoutGroup.rows) {
      var rowX = 0

      var currentHeight = 0
      for (view in row) {
        val scale = scaleFunc(view)
        val margin = view.getMargin(scale)
        val framePadding = padding.previewPaddingProvider(scale)
        rowX += framePadding + view.sizeFunc().width + margin.horizontal + framePadding
        currentHeight =
          max(currentHeight, framePadding + view.sizeFunc().height + margin.vertical + framePadding)
      }
      groupRequiredWidth = max(groupRequiredWidth, rowX)
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

    val contentToFit =
      if (StudioFlags.PREVIEW_DYNAMIC_ZOOM_TO_FIT.get()) {
        // Take into consideration both height and width
        content.take(max(1, availableWidth * availableHeight / minumumPreviewSpacePx))
      } else content

    // Use binary search to find the proper zoom-to-fit value.

    // Calculate the sum of the area of the original content sizes. This considers the margins and
    // paddings of every content.
    val rawSizes =
      contentToFit.map {
        val contentSize = it.contentSize
        val margin = it.getMargin(1.0)
        val framePadding = padding.previewPaddingProvider(1.0)
        Dimension(
          contentSize.width + margin.horizontal + framePadding * 2,
          contentSize.height + margin.vertical + framePadding * 2,
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
      contentToFit,
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
      val dim = getSize(content, { contentSize.scaleOf(max) }, { max }, width, cache)
      return if (dim.width <= width && dim.height <= height) max else min
    }
    val scale = (min + max) / 2
    val dim = getSize(content, { contentSize.scaleOf(scale) }, { scale }, width, cache)
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
    val isSameWidth = currentAvailableWidth == availableWidth
    currentAvailableWidth = availableWidth

    val content = group.content
    if (content.isEmpty()) {
      return GridLayoutGroup(group.header, emptyList())
    }

    // If the content in the windows hasn't changed and there is no additional content to add in the
    // layout group, we want to keep the layout as it is
    currentLayoutGroup?.let { layoutGroup ->
      if (SCROLLABLE_ZOOM_ON_GRID.get() && isSameWidth && content == layoutGroup.content()) {
        return layoutGroup
      }
    }

    return reLayout(scaleFunc, widthFunc, availableWidth, group).apply { currentLayoutGroup = this }
  }

  private fun reLayout(
    scaleFunc: PositionableContent.() -> Double,
    widthFunc: PositionableContent.() -> Int,
    availableWidth: Int,
    group: PositionableGroup,
  ): GridLayoutGroup {
    val gridList = mutableListOf<List<PositionableContent>>()
    val firstView = group.content.first()
    val firstPreviewFramePadding = padding.previewPaddingProvider(firstView.scaleFunc())
    var nextX =
      firstPreviewFramePadding +
        firstView.widthFunc() +
        firstView.getMargin(firstView.scaleFunc()).horizontal +
        firstPreviewFramePadding

    var columnList = mutableListOf(firstView)
    for (view in group.content.drop(1)) {
      val framePadding = padding.previewPaddingProvider(view.scaleFunc())
      val nextViewWidth =
        framePadding + view.widthFunc() + view.getMargin(view.scaleFunc()).horizontal + framePadding
      if (nextX + nextViewWidth > availableWidth) {
        nextX = nextViewWidth
        gridList.add(columnList)
        columnList = mutableListOf(view)
      } else {
        nextX += nextViewWidth
        columnList.add(view)
      }
    }
    gridList.add(columnList)
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

    if (content.size == 1) {
      val singleContent = content.single()
      // When there is only one visible preview, centralize it as a special case.
      val point = getSingleContentPosition(singleContent, availableWidth, availableHeight)

      return mapOf(singleContent to point)
    }

    val groups = transform(content)

    val startX: Int = padding.canvasLeftPadding
    val startY: Int = padding.canvasTopPadding

    var nextGroupY = startY

    val positionMap = mutableMapOf<PositionableContent, Point>()

    for (group in groups) {
      val layoutGroup =
        createLayoutGroup(group, { scale }, availableWidth) { scaledContentSize.width }
      var nextX = startX
      var nextY = nextGroupY
      var maxBottomInRow = 0

      fun measure(view: PositionableContent) {
        val framePadding = padding.previewPaddingProvider(view.scale)
        positionMap[view] = getContentPosition(view, nextX + framePadding, nextY + framePadding)
        nextX += framePadding + view.scaledContentSize.width + view.margin.horizontal + framePadding
        maxBottomInRow =
          max(
            maxBottomInRow,
            nextY +
              framePadding +
              view.margin.vertical +
              view.scaledContentSize.height +
              framePadding,
          )
      }

      layoutGroup.header?.let {
        measure(it)
        nextX = startX
        nextY = maxBottomInRow
      }

      for (row in layoutGroup.rows) {
        row.forEach { measure(it) }
        nextX = startX
        nextY = maxBottomInRow
      }

      nextGroupY = nextY
    }

    return positionMap
  }
}
