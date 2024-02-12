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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.actions.ZoomType
import java.awt.Point

/** An interface containing the listeners for zooming changes. */
interface ZoomListener {
  /**
   * Listen to a zoom change.
   *
   * @param update the update of the zoom change.
   */
  fun onZoomChange(update: ZoomChange)
}

/**
 * Defines a change of zoom type of [ZoomType].
 *
 * The difference with [ScaleChange] is there are no scale numbers but [ZoomType]s.
 *
 * @param zoomType the zoom type can be: [ZoomType.IN], [ZoomType.OUT], [ZoomType.ACTUAL] or
 *   [ZoomType.FIT]
 * @param hasScaleChanged True if the zoom change has changed scale value, false otherwise.
 * @param focusPoint the focus point where to apply the zoom change, default value is with
 *   coordinates of (-1, -1), meaning that when the zoom was changed, no coordinates were expressed
 *   (for example when we press the zoom-in or the zoom-out buttons).
 */
data class ZoomChange(
  val zoomType: ZoomType,
  val hasScaleChanged: Boolean,
  val focusPoint: Point = Point(-1, -1),
)
