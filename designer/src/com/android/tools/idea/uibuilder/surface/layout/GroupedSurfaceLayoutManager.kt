/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.idea.common.layout.positionable.margin
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import java.awt.Dimension
import java.awt.Point

/**
 * This layout puts the previews in the same group together using the [transform] function.
 *
 * If there is only one visible preview, put it at the center of window.
 *
 * @param previewFramePaddingProvider is to provide the horizontal and vertical paddings of every
 *   "preview frame". The "preview frame" is a preview with its toolbars.
 */
abstract class GroupedSurfaceLayoutManager(
  @SwingCoordinate private val previewFramePaddingProvider: (scale: Double) -> Int
) : SurfaceLayoutManager {

  override fun getRequiredSize(
    content: Collection<PositionableContent>,
    @SwingCoordinate availableWidth: Int,
    @SwingCoordinate availableHeight: Int,
    @SwingCoordinate dimension: Dimension?,
  ) = getSize(content, PositionableContent::scaledContentSize, { scale }, availableWidth, dimension)

  @SwingCoordinate
  protected fun getSingleContentPosition(
    content: PositionableContent,
    @SwingCoordinate availableWidth: Int,
    @SwingCoordinate availableHeight: Int,
  ): Point {
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

  /** Get the actual position should be set to the given [PositionableContent] */
  @SwingCoordinate
  protected fun getContentPosition(
    content: PositionableContent,
    @SwingCoordinate previewX: Int,
    @SwingCoordinate previewY: Int,
  ): Point {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = previewX + margin.left
    val shiftedY = previewY + margin.top
    return Point(shiftedX, shiftedY)
  }

  protected abstract fun getSize(
    content: Collection<PositionableContent>,
    sizeFunc: PositionableContent.() -> Dimension,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
    dimension: Dimension?,
  ): Dimension

  protected abstract val transform: (Collection<PositionableContent>) -> List<PositionableGroup>
}
