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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.google.common.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

object PowerRailTableUtils {
  private fun isPowerRangeInvalid(lowerBoundTs: Long, upperBoundTs: Long) = lowerBoundTs >= upperBoundTs

  fun computeCumulativeEnergyInRange(powerUsageRange: PowerUsageRange) : Long {
    val lowerBound = powerUsageRange.lowerBound
    val upperBound = powerUsageRange.upperBound
    val lowerBoundTs = lowerBound.x
    val upperBoundTs = upperBound.x
    return if (isPowerRangeInvalid(lowerBoundTs, upperBoundTs)) {
      0
    }
    else {
      upperBound.value - lowerBound.value
    }
  }

  fun computeAveragePowerInRange(powerUsageRange: PowerUsageRange) : Double {
    val lowerBound = powerUsageRange.lowerBound
    val upperBound = powerUsageRange.upperBound
    val lowerBoundTs = lowerBound.x
    val upperBoundTs = upperBound.x
    val durationMs = TimeUnit.MICROSECONDS.toMillis(upperBoundTs - lowerBoundTs)
    return if (isPowerRangeInvalid(lowerBoundTs, upperBoundTs) || durationMs == 0L) {
      0.0
    }
    else {
      val cumulativeEnergy = computeCumulativeEnergyInRange(powerUsageRange)
      // The time is in micro-seconds, so this converts it to milliseconds.
      cumulativeEnergy / durationMs.toDouble()
    }
  }

  data class PowerUsageRange(
    val lowerBound: SeriesData<Long>,
    val upperBound: SeriesData<Long>
  )

  /**
   * Computes and returns the cumulative power used in the passed-in range.
   */
  fun computePowerUsageRange(cumulativeData: List<SeriesData<Long>>, selectionRange: Range): PowerUsageRange {
    val lowerBound = getLowerBoundDataInRange(cumulativeData, selectionRange.min)
    val upperBound = getUpperBoundDataInRange(cumulativeData, selectionRange.max)
    // When the range selection only contains one or zero data points, it is possible for the
    // upper bound's timestamp (x) to be less than or equal to the lower bound's timestamp (x).
    // Thus, in this case, the cumulative value should be 0 as there is no positive difference
    // in start and end power values.
    return PowerUsageRange(lowerBound, upperBound)
  }

  /**
   * Returns the lower bound of the power data according to the passed in [minTs] timestamp.
   * Because the data points of the power rails will most likely not match up exactly with
   * the user's selected range, we must be able to find and return the smallest SeriesData
   * greater than the range's min (passed in as [minTs]) as the lower bound data.
   */
  @VisibleForTesting
  fun getLowerBoundDataInRange(data: List<SeriesData<Long>>, minTs: Double): SeriesData<Long> {
    return getBoundPowerSeriesData(data, minTs, isLowerBound = true)
  }

  /**
   * Returns the upper bound of the power data w.r.t. the passed in [maxTs] timestamp.
   * Because the data points of the power rails will most likely not match up exactly with
   * the user's selected range, we must be able to find and return the greatest SeriesData
   * smaller than the range's max (passed in as [maxTs]) as the upper bound data.
   */
  @VisibleForTesting
  fun getUpperBoundDataInRange(data: List<SeriesData<Long>>, maxTs: Double): SeriesData<Long> {
    return getBoundPowerSeriesData(data, maxTs, isLowerBound = false)
  }

  /**
   * Helper function to find the lower and upper bounded SeriesData by performing a binary search on
   * the passed in timestamp. If the timestamp exists in the SeriesData, we can return that SeriesData
   * element, otherwise, if a lower bound is being sought, we return the smallest element's timestamp
   * greater than the target timestamp, and for the upper bound we return the greatest element's
   * timestamp less than the target timestamp.
   *
   * Assumes and asserts [data] is a non-empty list of SeriesData.
   */
  private fun getBoundPowerSeriesData(data: List<SeriesData<Long>>, targetTs: Double, isLowerBound: Boolean): SeriesData<Long> {
    assert(data.isNotEmpty())

    val index = seriesDataBinarySearch(data, targetTs.toLong())

    // If index is non-negative, it was found.
    if (index >= 0) {
      return data[index]
    }

    // Otherwise, as described by the binarySearch method documentation,
    // it returns the "the inverted insertion point (-(insertion point) - 1)".
    // We can reverse the decrement and negation to get the actual ideal
    // insertion point. This can be used to determine the lower and upper
    // bound below.
    val insertionPos = (index + 1) * -1

    return if (isLowerBound) {
      // If the calling code is seeking the lower bounded value,
      // the insertion position would be smallest value greater
      // than the target, naturally making it the lower bound.
      if (insertionPos < data.size) {
        data[insertionPos]
      }
      // If the insertion position is at the end of the searched
      // data, then we return the last data point.
      else {
        data[data.size - 1]
      }
    }
    // Otherwise, for the upper bound value, we must compute the
    // greatest value smaller than the target.
    else {
      // In the case where the insertion position would be 0, this means
      // that the max value is smaller than all data points. So, we just
      // return the first data point.
      if (insertionPos == 0) {
        data[insertionPos]
      }
      // If the insertion point is non-zero, then the greatest value smaller
      // than the target would be the element before the insertion position.
      else {
        data[insertionPos - 1]
      }
    }
  }

  private fun seriesDataBinarySearch(data: List<SeriesData<Long>>, target: Long): Int {
    return data.binarySearch<SeriesData<Long>>(SeriesData(target, 0), { a, b -> a.x.compareTo(b.x) })
  }

  const val POWER_RAIL_TOTAL_VALUE_IN_RANGE_TOOLTIP_MSG = "Power data is sampled in 250ms intervals.<br><br>" +
                                                          "The total energy number represented<br>" +
                                                          "contains an <b><i>error margin of up to +/- 0.5<br>" +
                                                          "seconds (500ms)</i></b> of power data due to the<br>" +
                                                          "sampling interval."
}