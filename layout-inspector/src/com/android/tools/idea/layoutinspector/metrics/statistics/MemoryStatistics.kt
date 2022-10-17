/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.tools.idea.layoutinspector.memory.InspectorMemoryProbe
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorMemory

// 1 Mb in bytes
@VisibleForTesting
const val ONE_MB = 1000000

class MemoryStatistics(model: InspectorModel) {
  @Suppress("unused")
  private val memoryProbe = InspectorMemoryProbe(model, this)

  /**
   * True if the initial display used skia images.
   */
  private var initialHasSkiaImages = false

  /**
   * The size in bytes required by the model on the initial display.
   */
  private var initialModelSize: Long = 0

  /**
   * The time in milliseconds it took to compute the size of the model on the initial display.
   */
  private var initialModelTime: Long = 0

  /**
   * True if the largest model used skia images.
   */
  private var largestHasSkiaImages = false

  /**
   * The size in bytes required by the largest model in the session.
   */
  private var largestModelSize: Long = 0

  /**
   * The time in milliseconds it took to compute the size of the largest model in the session.
   *
   * This number can be used to determine if we are using too much time on computing the size in the field.
   */
  private var largestModelTime: Long = 0

  /**
   * For testing: how many measurements was performed.
   */
  var measurements = 0
    private set

  fun start() {
    initialHasSkiaImages = false
    initialModelSize = 0L
    initialModelTime = 0L
    largestHasSkiaImages = false
    largestModelSize = 0L
    largestModelTime = 0L
    measurements = 0
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(dataSupplier: () -> DynamicLayoutInspectorMemory.Builder) {
    if (initialHasSkiaImages || initialModelSize > 0 || initialModelTime > 0 ||
        largestHasSkiaImages || largestModelSize > 0 || largestModelTime > 0) {
      dataSupplier().let {
        it.initialSnapshotBuilder.skiaImage = initialHasSkiaImages
        it.initialSnapshotBuilder.captureSizeMb = initialModelSize / ONE_MB
        it.initialSnapshotBuilder.measurementDurationMs = initialModelTime
        it.largestSnapshotBuilder.skiaImage = largestHasSkiaImages
        it.largestSnapshotBuilder.captureSizeMb = largestModelSize / ONE_MB
        it.largestSnapshotBuilder.measurementDurationMs = largestModelTime
      }
    }
  }

  fun recordModelSize(hasSkiaImages: Boolean, size: Long, time: Long) {
    if (size > 0 && initialModelSize == 0L) {
      initialHasSkiaImages = hasSkiaImages
      initialModelSize = size
      initialModelTime = time
    }
    if (size > largestModelSize) {
      largestHasSkiaImages = hasSkiaImages
      largestModelSize = size
      largestModelTime = time
    }
    measurements++
  }
}
