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
package com.android.tools.idea.common.surface.layout

import com.android.tools.adtui.common.SwingCoordinate
import java.awt.Dimension
import java.awt.Point

/**
 * When the view size is changed, use the top-left corner of the visible area as the anchor to keep
 * the scrolling position after zooming.
 */
class TopLeftCornerScroller(
  @SwingCoordinate oldViewSize: Dimension,
  @SwingCoordinate scrollPosition: Point,
  oldScale: Double,
  newScale: Double,
) :
  ReferencePointScroller(
    oldViewSize,
    scrollPosition,
    Point(scrollPosition.x, scrollPosition.y),
    oldScale,
    newScale,
  )
