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
import com.android.tools.idea.common.layout.positionable.margin
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.uibuilder.layout.padding.OrganizationPadding
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.MAX_ITERATION_TIMES
import com.android.tools.idea.uibuilder.surface.layout.MINIMUM_SCALE
import com.android.tools.idea.uibuilder.surface.layout.SCALE_UNIT
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.android.tools.idea.uibuilder.surface.layout.vertical
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.annotations.ApiStatus

/** The minumum height what should be available for the preview. */
private const val minumumPreviewHeightPx = 65

/**
 * Experimental list layout. All previews are organized in groups.
 *
 * TODO(b/321949200) Add tests
 */
@ApiStatus.Experimental
class ListLayoutManager(
  private val padding: OrganizationPadding,
  override val transform: (Collection<PositionableContent>) -> List<PositionableGroup>,
) : GroupedSurfaceLayoutManager(padding.previewPaddingProvider) {

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
    val maxNumberOfPreviews = min(content.size, availableHeight / minumumPreviewHeightPx)
    val maxWidth =
      content.maxOf { it.contentSize.width } +
        padding.canvasLeftPadding +
        padding.groupLeftPadding +
        40
    // The zoom-to-fit scale can not be larger than this scale - even the largest preview should
    // fit.
    val upperBound = availableWidth.toDouble() / maxWidth

    if (upperBound <= MINIMUM_SCALE) {
      return MINIMUM_SCALE
    }
    // binary search between MINIMUM_SCALE to upper bound.
    return getMaxZoomToFitScale(
      content.take(maxNumberOfPreviews),
      MINIMUM_SCALE,
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
      val dim = getSize(content, { contentSize.scaleBy(max) }, { max }, 0, cache)
      return if (dim.width <= width && dim.height <= height) max else min
    }
    val scale = (min + max) / 2
    val dim = getSize(content, { contentSize.scaleBy(scale) }, { scale }, 0, cache)
    return if (dim.width <= width && dim.height <= height) {
      getMaxZoomToFitScale(content, scale, max, width, height, cache, depth + 1)
    } else {
      getMaxZoomToFitScale(content, min, scale, width, height, cache, depth + 1)
    }
  }

  /** Size of the layout. Takes into account all paddings and offsets. */
  override fun getSize(
    content: Collection<PositionableContent>,
    sizeFunc: PositionableContent.() -> Dimension,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
    dimension: Dimension?,
  ): Dimension {
    val dim = dimension ?: Dimension()

    val groups = transform(content)

    if (groups.isEmpty()) {
      dim.setSize(0, 0)
      return dim
    }

    var requiredWidth = 0
    var totalRequiredHeight = padding.canvasTopPadding

    fun updateSize(view: PositionableContent, addGroupOffset: Boolean) {
      val scale = view.scaleFunc()
      val margin = view.getMargin(scale)
      val viewSize = view.sizeFunc()
      val viewWidth =
        if (addGroupOffset) padding.groupLeftPadding
        else 0 + viewSize.width + margin.horizontal + padding.previewRightPadding(scale, view)
      val requiredHeight =
        viewSize.height + margin.vertical + padding.previewBottomPadding(scale, view)

      requiredWidth = maxOf(requiredWidth, viewWidth)
      totalRequiredHeight += requiredHeight
    }

    groups.forEach { group ->
      group.header?.let { updateSize(it, false) }
      group.content.forEach { view -> updateSize(view, group.hasHeader) }
    }

    dim.setSize(requiredWidth + padding.canvasLeftPadding, max(0, totalRequiredHeight))
    return dim
  }

  override fun measure(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
    keepPreviousPadding: Boolean,
  ): Map<PositionableContent, Point> {
    val groups = transform(content)
    if (groups.isEmpty()) {
      return emptyMap()
    }

    if (content.size == 1 && content.first() !is HeaderPositionableContent) {
      val singleContent = content.single()
      // When there is only one visible preview, centralize it as a special case.
      val point = getSingleContentPosition(singleContent, availableWidth, availableHeight)

      return mapOf(singleContent to point)
    }
    val positionMap = mutableMapOf<PositionableContent, Point>()

    fun getHeight(view: PositionableContent): Int {
      return view.scaledContentSize.height +
        view.margin.vertical +
        padding.previewBottomPadding(view.scale, view)
    }

    var nextY = padding.canvasTopPadding
    groups.forEach { group ->
      group.header?.let {
        positionMap.setContentPosition(it, padding.canvasLeftPadding, nextY)
        nextY += getHeight(it)
      }
      val xPosition =
        padding.canvasTopPadding + if (group.hasHeader) padding.groupLeftPadding else 0
      group.content.forEach { view ->
        positionMap.setContentPosition(view, xPosition, nextY)
        nextY += getHeight(view)
      }
    }
    return positionMap
  }

  private fun MutableMap<PositionableContent, Point>.setContentPosition(
    content: PositionableContent,
    x: Int,
    y: Int,
  ) {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = x + margin.left
    val shiftedY = y + margin.top
    put(content, Point(shiftedX, shiftedY))
  }
}
