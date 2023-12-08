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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.TimelineZoomHelper
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.adtui.model.updater.Updater
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class NetworkTimeline(updater: Updater) : DefaultTimeline(), Updatable {
  private val zoomLeft = Range(0.0, 0.0)
  private val zoomHelper = TimelineZoomHelper(dataRange, viewRange, zoomLeft)

  init {
    updater.register(this)
  }

  override fun resetZoom() {
    viewRange.set(max(0.0, dataRange.max - VIEW_LENGTH_US), dataRange.max)
  }

  override fun handleMouseWheelZoom(count: Double, anchor: Double) {
    zoomHelper.zoom(count * viewRange.length * 0.25, anchor)
  }

  override fun zoomIn() {
    zoomHelper.zoom(-viewRange.length * DEFAULT_ZOOM_RATIO, 0.5)
  }

  override fun zoomOut() {
    zoomHelper.zoom(viewRange.length * DEFAULT_ZOOM_RATIO, 0.5)
  }

  override fun update(elapsedNs: Long) {
    zoomHelper.handleZoomView(elapsedNs)
  }

  override fun frameViewToRange(targetRange: Range) {
    super.frameViewToRange(targetRange)
    zoomHelper.updateZoomLeft(targetRange, PADDING_RATIO)
  }

  companion object {
    val VIEW_LENGTH_US = TimeUnit.SECONDS.toMicros(30)
  }
}
