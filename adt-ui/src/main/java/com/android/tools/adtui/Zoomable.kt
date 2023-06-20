/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.intellij.openapi.actionSystem.DataKey

@JvmField
val ZOOMABLE_KEY = DataKey.create<Zoomable>(Zoomable::class.java.name)

interface Zoomable {
  /**
   * The scaling level of zoomable component.
   */
  val scale: Double

  /**
   * A factor gives a chance to adjust [scale] value in different visual components.
   * The visual size is [scale] * [screenScalingFactor].
   * This value must be positive.
   */
  val screenScalingFactor: Double

  fun zoom(type: ZoomType): Boolean

  fun canZoomIn(): Boolean
  fun canZoomOut(): Boolean
  fun canZoomToFit(): Boolean
  fun canZoomToActual(): Boolean

  /**
   * Execute a zoom on the content. See [ZoomType] for the different types of zoom available.
   *
   * If type is [ZoomType.IN], zoom toward the given view coordinates.
   * If [x] or [y] are negative, zoom toward the center of the viewport.
   *
   * @return True if the scaling was changed, false if this was a noop.
   */
  fun zoom(type: ZoomType, @SwingCoordinate x: Int, @SwingCoordinate y: Int): Boolean = zoom(type)
}