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
package com.android.tools.adtui.model

import com.android.tools.adtui.model.updater.Updater
import kotlin.math.max

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

/**
 * In order to prevent attempts to zoom larger than the current view range, this cap serves to limit
 * the delta range to a fixed number proportional to the current view range.
 */
private const val ZOOM_IN_DELTA_RANGE_US_MAX_RATIO = 0.90

/** How many nanoseconds left in our zoom before we just clamp to our final value. */
private const val ZOOM_LERP_THRESHOLD_NS = 10.0

/** Facilitates some zoom related operations that are used Timelines */
class TimelineZoomHelper(
  private val dataRange: Range,
  private val viewRange: Range,
  private val zoomLeft: Range,
) {

  /**
   * Calculates a zoom within the current data bounds. If a zoom extends beyond data max the left
   * over is applied to the view minimum.
   *
   * @param amountUs the amount of time request to change the view by.
   * @param ratio a ratio between 0 and 1 that determines the focal point of the zoom. 1 applies the
   *   full delta to the min while 0 applies the full delta to the max.
   */
  fun zoom(amountUs: Double, ratio: Double) {
    var deltaUs = amountUs
    if (deltaUs == 0.0) {
      return
    }
    if (deltaUs < 0.0) {
      val zoomMax = -ZOOM_IN_DELTA_RANGE_US_MAX_RATIO * viewRange.length
      deltaUs = max(zoomMax, deltaUs)
    }
    zoomLeft.clear()
    var minUs = viewRange.min - deltaUs * ratio
    var maxUs = viewRange.max + deltaUs * (1 - ratio)
    // When the view range is not fully covered, reset minUs to data range could change zoomLeft
    // from zero to a large number.
    val isDataRangeFullyCoveredByViewRange: Boolean = dataRange.min <= viewRange.min
    if (isDataRangeFullyCoveredByViewRange && minUs < dataRange.min) {
      maxUs += dataRange.min - minUs
      minUs = dataRange.min
    }
    // If our new view range is less than our data range then lock our max view, so we
    // don't expand it beyond the data range max.
    if (!isDataRangeFullyCoveredByViewRange && minUs < dataRange.min) {
      maxUs = dataRange.max
    }
    if (maxUs > dataRange.max) {
      minUs -= maxUs - dataRange.max
      maxUs = dataRange.max
    }
    // minUs could have gone past again.
    if (isDataRangeFullyCoveredByViewRange) {
      minUs = max(minUs, dataRange.min)
    }
    zoomLeft.set(minUs - viewRange.min, maxUs - viewRange.max)
  }

  /** Updates [zoomLeft] after a timeline `frameViewToRange` call */
  fun updateZoomLeft(targetRange: Range, paddingRatio: Double) {
    val finalRange =
      Range(
        targetRange.min - targetRange.length * paddingRatio,
        targetRange.max + targetRange.length * paddingRatio
      )

    // Cap requested view to max data.
    if (finalRange.max > dataRange.max) {
      finalRange.max = dataRange.max
    }
    zoomLeft.set(finalRange.min - viewRange.min, finalRange.max - viewRange.max)
  }

  /**
   * Handles updating the view range by the delta stored in our {@link #myZoomLeft} value. If we
   * have a delta stored in {@link #myZoomLeft} we apply a percentage of that value to our current
   * view, and reduce the delta currently stored. Eg: View = 10, 100 myZoomLeft = 30,-30 After we
   * call this function we end up with View = 20, 90 myZoomLeft = 20, -20.
   */
  fun handleZoomView(elapsedNs: Long) {
    if (zoomLeft.min != 0.0 || zoomLeft.max != 0.0) {
      val min = Updater.lerp(0.0, zoomLeft.min, 0.99999f, elapsedNs, ZOOM_LERP_THRESHOLD_NS)
      var max = Updater.lerp(0.0, zoomLeft.max, 0.99999f, elapsedNs, ZOOM_LERP_THRESHOLD_NS)
      zoomLeft.set(zoomLeft.min - min, zoomLeft.max - max)
      if ((viewRange.max + max) > dataRange.max) {
        max = dataRange.max - viewRange.max
      }
      viewRange.set(viewRange.min + min, viewRange.max + max)
    }
  }
}
