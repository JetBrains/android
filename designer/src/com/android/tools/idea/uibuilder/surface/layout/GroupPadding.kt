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

/**
 * Paddings for grouped layout.
 *
 * @param canvasTopPadding is the top padding from the surface.
 * @param canvasLeftPadding is the left padding from the surface.
 * @param previewPaddingProvider is to provide the horizontal and vertical paddings of every. The
 *   input value is the scale value of the current [PositionableContent].
 */
open class GroupPadding(
  @SwingCoordinate val canvasTopPadding: Int,
  @SwingCoordinate val canvasLeftPadding: Int,
  @SwingCoordinate val previewPaddingProvider: (scale: Double) -> Int,
)

/** Paddings for layouts with organization. */
class OrganizationPadding(
  @SwingCoordinate canvasTopPadding: Int,
  @SwingCoordinate canvasLeftPadding: Int,
  @SwingCoordinate val groupLeftPadding: Int,
  @SwingCoordinate previewPaddingProvider: (scale: Double) -> Int,
  @SwingCoordinate val previewRightPadding: (scale: Double, content: PositionableContent) -> Int,
  @SwingCoordinate val previewBottomPadding: (scale: Double, content: PositionableContent) -> Int,
) : GroupPadding(canvasTopPadding, canvasLeftPadding, previewPaddingProvider)
