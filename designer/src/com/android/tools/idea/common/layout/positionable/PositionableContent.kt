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
package com.android.tools.idea.common.layout.positionable

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import java.awt.Dimension
import java.awt.Insets

/**
 * Class that provides an interface for content that can be positioned on the
 * [com.android.tools.idea.common.surface.DesignSurface]
 */
interface PositionableContent {

  /** [PositionableContent] are grouped by [organizationGroup]. */
  val organizationGroup: OrganizationGroup?

  /** The current scale value of this [PositionableContent]. */
  @SurfaceScale val scale: Double

  val contentSize: Dimension
    @AndroidDpCoordinate get() = getContentSize(Dimension())

  /**
   * The height of the top panel of a [PositionableContent], top panel is not affected by scale
   * changes, so this value should be used when we need a fine calculation of a
   * [PositionableContent] having a top panel
   */
  val topPanelHeight: Int
    get() = 0

  @get:SwingCoordinate val x: Int

  @get:SwingCoordinate val y: Int

  /**
   * This value is true if this [PositionableContent] has got the focus in the surface (eg: is
   * selected), false otherwise.
   */
  val isFocusedContent: Boolean

  /**
   * Returns the current size of the view content, excluding margins. This doesn't account the
   * current [scale].
   */
  @AndroidDpCoordinate fun getContentSize(dimension: Dimension?): Dimension

  fun setLocation(@SwingCoordinate x: Int, @SwingCoordinate y: Int)

  /** Get the margin value with the given scale. */
  fun getMargin(scale: Double): Insets
}

/** Sorts the [Collection<PositionableContent>] by its x and y coordinates. */
internal fun Collection<PositionableContent>.sortByPosition() =
  sortedWith(compareBy({ it.y }, { it.x }))

/** Get the margin with the current [PositionableContent.scale] value. */
val PositionableContent.margin: Insets
  get() = getMargin(scale)

val PositionableContent.scaledContentSize: Dimension
  @SwingCoordinate get() = getScaledContentSize(Dimension())

/**
 * Returns the current size of the view content, excluding margins. This is the same as
 * {@link #getContentSize()} but accounts for the current [PositionableContent.scale].
 *
 * This function is implemented as an extension because it should not be overridden.
 *
 * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the
 *   values will be set and this instance returned.
 */
@SwingCoordinate
fun PositionableContent.getScaledContentSize(dimension: Dimension?): Dimension =
  getContentSize(dimension).scaleBy(scale)

/**
 * Calculate the total height of a [PositionableContent] adding a scale factor only on parts of the
 * content that are subject to scale change. Scaled height is calculated considering that
 * [PositionableContent.topPanelHeight] is not affected by scale changes, while margins and content
 * are.
 *
 * @param height the height of the content where we want to calculate the zoom minus
 * @param scale the scale to apply during the calculation of the height
 *   ([PositionableContent.topPanelHeight] is not affected by this value.
 * @returns the scaled height plus the offset that is not subject for scale change.
 */
fun PositionableContent.calculateHeightWithOffset(height: Int, scale: Double): Int {
  // contentSize takes into account top panel, when we apply the scale we also multiply the size of
  // the top panel.
  val scaledTotalHeight = height
  // The value we want to remove from the scaled total height is the top panel with the scale factor
  // applied.
  val scaledTopPanelHeight = topPanelHeight * scale

  // This is the height of the content of the PositionableContent without any top panel
  val contentHeight = scaledTotalHeight - scaledTopPanelHeight
  // We add the not scaled top panel height to the content height, to have the correct size.
  val scaledHeight = (contentHeight + topPanelHeight).toInt()
  return scaledHeight
}
