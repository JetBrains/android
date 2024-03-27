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

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.surface.SurfaceScale
import java.awt.Dimension
import java.awt.Insets

/**
 * Class that provides an interface for content that can be positioned on the
 * [com.android.tools.idea.common.surface.DesignSurface]
 */
interface PositionableContent {

  /** [PositionableContent] are grouped by [organizationGroup]. */
  val organizationGroup: String?

  /** The current scale value of this [PositionableContent]. */
  @SurfaceScale val scale: Double

  val contentSize: Dimension
    @AndroidDpCoordinate get() = getContentSize(Dimension())

  @get:SwingCoordinate val x: Int

  @get:SwingCoordinate val y: Int

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
