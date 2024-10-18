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
package com.android.tools.idea.uibuilder.layout.padding

import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent

val PREVIEW_FRAME_PADDING_PROVIDER: (Double) -> Int = { scale -> dynamicPadding(scale, 5, 20) }

/**
 * Provider of the horizontal and vertical paddings for preview. The input value is the scale value
 * of the current [PositionableContent].
 */
private val ORGANIZATION_PREVIEW_RIGHT_PADDING: (Double, PositionableContent) -> Int =
  { scale, content ->
    if (content is HeaderPositionableContent) dynamicPadding(scale, 0, 0)
    else dynamicPadding(scale, 5, 15)
  }

/**
 * Provider of the horizontal and vertical paddings for preview. The input value is the scale value
 * of the current [PositionableContent].
 */
private val ORGANIZATION_PREVIEW_BOTTOM_PADDING: (Double, PositionableContent) -> Int =
  { scale, content ->
    if (content is HeaderPositionableContent) dynamicPadding(scale, 0, 0)
    else dynamicPadding(scale, 5, 7)
  }

/** Default paddings for layouts with organization. */
val DEFAULT_LAYOUT_PADDING =
  OrganizationPadding(
    10,
    10,
    10,
    24,
    PREVIEW_FRAME_PADDING_PROVIDER,
    ORGANIZATION_PREVIEW_RIGHT_PADDING,
    ORGANIZATION_PREVIEW_BOTTOM_PADDING,
  )

/**
 * Provider of the padding for preview. The input value is the scale value of the current
 * [PositionableContent]. Minimum padding is min at 20% and maximum padding is max at 100%,
 * responsive.
 */
private fun dynamicPadding(scale: Double, min: Int, max: Int): Int =
  when {
    scale <= 0.2 -> min
    scale >= 1.0 -> max
    else ->
      min + ((max - min) / (1 - 0.2)) * (scale - 0.2) // find interpolated value between min and max
  }.toInt()
