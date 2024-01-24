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

/**
 * Describes a component with zoom controls available.
 * This interface is used by zoom actions.
 * */
interface Zoomable {
  /**
   * The scaling level of a component.
   */
  val scale: Double

  /**
   * A factor gives a chance to adjust [scale] value in different visual components.
   * The visual size is [scale] * [screenScalingFactor].
   * This value must be positive.
   */
  val screenScalingFactor: Double

  /**
   * Executes a zoom on the content of the component.
   * See [ZoomType] for the different types of zoom available.
   *
   * @return True if the scaling was changed, false if this was a noop.
   */
  fun zoom(type: ZoomType): Boolean

  /**
   * @return true if it is possible to zoom in on the component, false otherwise.
   */
  fun canZoomIn(): Boolean

  /**
   * @return true if it is possible to zoom out on the component, false otherwise.
   */
  fun canZoomOut(): Boolean

  /**
   * @return true if it is possible to apply zoom-to-fit action on the component, false otherwise.
   */
  fun canZoomToFit(): Boolean

  /**
   * @return true if it is possible to apply zoom-to-actual action on the component, false otherwise.
   */
  fun canZoomToActual(): Boolean

  /**
   * Executes a zoom on the content of the component.
   * See [ZoomType] for the different types of zoom available.
   *
   * If type is [ZoomType.IN], zoom toward the given view coordinates.
   * If [x] or [y] are negative, zoom toward the center of the viewport.
   *
   * @return True if the scaling was changed, false if this was a noop.
   */
  fun zoom(type: ZoomType, @SwingCoordinate x: Int, @SwingCoordinate y: Int): Boolean = zoom(type)
}