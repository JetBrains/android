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
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.SurfaceScale
import java.awt.Dimension
import java.awt.Insets

/**
 * Sorts the [Collection<PositionableContent>] by its x and y coordinates.
 */
internal fun Collection<PositionableContent>.sortByPosition() = sortedWith(compareBy({ it.y }, { it.x }))

/**
 * Class that provides an interface for content that can be positioned on the [com.android.tools.idea.common.surface.DesignSurface]
 */
interface PositionableContent {

  val groupId: String?

  /**
   * The current scale value of this [PositionableContent].
   */
  @SurfaceScale
  val scale: Double

  val contentSize: Dimension
    @AndroidDpCoordinate get() = getContentSize(Dimension())

  @get:SwingCoordinate
  val x: Int

  @get:SwingCoordinate
  val y: Int

  val margin: Insets

  val isVisible: Boolean

  /**
   * Returns the current size of the view content, excluding margins. This doesn't account the current [scale].
   */
  @AndroidDpCoordinate
  fun getContentSize(dimension: Dimension?): Dimension

  fun setLocation(@SwingCoordinate x: Int, @SwingCoordinate y: Int)
}

val PositionableContent.scaledContentSize: Dimension
  @SwingCoordinate get() = getScaledContentSize(Dimension())

/**
 * Returns the current size of the view content, excluding margins. This is the same as {@link #getContentSize()} but accounts for the
 * current [PositionableContent.scale].
 *
 * This function is implemented as an extension because it should not be overridden.
 *
 * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
 *                  returned.
 */
@SwingCoordinate
fun PositionableContent.getScaledContentSize(dimension: Dimension?): Dimension = getContentSize(dimension).scaleBy(scale)

/**
 * Interface used to layout and measure the size of [PositionableContent]s in [com.android.tools.idea.common.surface.DesignSurface].
 */
@Deprecated("The functionality here will be migrated to the SceneViewLayoutManager")
interface SurfaceLayoutManager {

  /**
   * Get the total content size of [PositionableContent]s when available display size is [availableWidth] x [availableHeight].
   * The size is for containing the raw size of [PositionableContent]s. It doesn't consider the zoom level of the given [PositionableContent]s.
   *
   * @param content all [PositionableContent]s to be measured.
   * @param availableWidth  the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param dimension used to store the result size. The new [Dimension] instance is created if the given instance is null.
   *
   * @see [getRequiredSize]
   */
  fun getPreferredSize(content: Collection<PositionableContent>,
                       @SwingCoordinate availableWidth: Int,
                       @SwingCoordinate availableHeight: Int,
                       @SwingCoordinate dimension: Dimension?): Dimension

  /**
   * Get the total content size of the given [PositionableContent]s when available display size is [availableWidth] x [availableHeight].
   * Not like [getPreferredSize], this considers the current zoom level of the given [PositionableContent]s.
   *
   * @param content all [PositionableContent]s to be measured.
   * @param availableWidth the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param dimension used to store the result size. The new [Dimension] instance is created if the given instance is null.
   *
   * @see [getPreferredSize]
   */
  fun getRequiredSize(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int,
                      @SwingCoordinate dimension: Dimension?): Dimension

  /**
   * Get the fit into scale value which can display all the [PositionableContent] in the given [availableWidth] x [availableHeight] range.
   */
  @SurfaceScale
  fun getFitIntoScale(content: Collection<PositionableContent>,
                      @SwingCoordinate availableWidth: Int,
                      @SwingCoordinate availableHeight: Int): Double

  /**
   * Place the given [PositionableContent]s in the proper positions by using [PositionableContent.setLocation]
   * Note that it only changes the locations of [PositionableContent]s but doesn't change their sizes.
   *
   * @param content all [PositionableContent]s to be laid out.
   * @param availableWidth the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param keepPreviousPadding true if all padding values should be the same as current one. This happens when resizing
   * the [PositionableContent].
   */
  fun layout(content: Collection<PositionableContent>,
             @SwingCoordinate availableWidth: Int,
             @SwingCoordinate availableHeight: Int,
             keepPreviousPadding: Boolean = false)
}
