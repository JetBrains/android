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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.Zoomable

/** Applies zoom changes to a Zoomable surface */
interface ZoomController : Zoomable {

  /**
   * Sets the scale factor used to multiply content size.
   *
   * @param scale The scale factor. Can be any value, but it will be capped between -1 and 10 (value
   *   below 0 means zoom to fit)
   * @param x the horizontal coordinate to where to focus the scaling
   * @param y the vertical coordinate to where to focus the scaling
   * @return True if the scaling was changed, false otherwise.
   */
  fun setScale(@SurfaceScale scale: Double, x: Int, y: Int): Boolean

  /**
   * Set the scale factor used to multiply the content size.
   *
   * @param scale The scale factor. Can be any value, but it will be capped between -1 and 10 (value
   *   below 0 means zoom to fit)
   * @return True if the scaling was changed, false if this was a noop.
   */
  fun setScale(@SurfaceScale scale: Double): Boolean {
    return setScale(scale, -1, -1)
  }

  /** Returns true if zoom to fit is applied */
  fun zoomToFit(): Boolean
}
