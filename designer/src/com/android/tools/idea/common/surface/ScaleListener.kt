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

import java.awt.Point

/** An interface containing the listeners for scale changes. */
interface ScaleListener {
  /**
   * Listens to any scale changes.
   *
   * @param update Value with an update of the scale.
   */
  fun onScaleChange(update: ScaleChange)
}

/**
 * Defines a change of the surface scale.
 *
 * @param previousScale The scale change before its change.
 * @param newScale The scale change after its change.
 * @param focusPoint The focus point where to apply the scale change, default value is with
 *   coordinates of (-1, -1), meaning that when the scale was changed, no focus coordinates were
 *   expressed. In such case we need to calculate the coordinates by checking the window size.
 * @param isAnimating Scale change is requested by an animation.
 */
data class ScaleChange(
  val previousScale: Double,
  val newScale: Double,
  val focusPoint: Point = Point(-1, -1),
  val isAnimating: Boolean = false,
)
