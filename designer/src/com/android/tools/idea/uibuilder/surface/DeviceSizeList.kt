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
package com.android.tools.idea.uibuilder.surface

import com.android.sdklib.AndroidCoordinate
import com.android.sdklib.devices.Device
import com.google.common.annotations.VisibleForTesting

/** List of [DeviceSize]. Optimized for [snapToDevice] */
class DeviceSizeList {

  /**
   * Pair [Device] and its size (x, y) Android coordinate. Use [create] to construct. Use [rotate]
   * to represent landscape.
   */
  class DeviceSize
  private constructor(
    val device: Device,
    @AndroidCoordinate val x: Int,
    @AndroidCoordinate val y: Int,
    var rotate: Boolean = false
  ) : Comparable<DeviceSize> {

    override fun compareTo(other: DeviceSize): Int {
      return COMPARATOR.compare(this, other)
    }

    companion object {
      private val COMPARATOR =
        Comparator.comparingInt<DeviceSize> { it.x }.thenComparingInt { it.y }

      /** Create DeviceSize where x <= y always. This is useful for sorting. */
      fun create(device: Device, x: Int, y: Int): DeviceSize {
        if (x <= y) {
          return DeviceSize(device, x, y)
        }
        return DeviceSize(device, y, x, true)
      }
    }

    fun rotate(): DeviceSize {
      return DeviceSize(device, y, x, true)
    }

    override fun equals(other: Any?): Boolean {
      if (other !is DeviceSize) {
        return false
      }
      return device == other.device && x == other.x && y == other.y && rotate == other.rotate
    }
  }

  @VisibleForTesting val myList = ArrayList<DeviceSize>()

  /** Add new device size to the list. Make sure that at the end of the add call [sort] */
  fun add(device: Device, px: Int, py: Int) {
    myList.add(DeviceSize.create(device, px, py))
  }

  fun sort() {
    myList.sort()
  }

  /**
   * Precondition: [sort] must have been called at least once. Find the device that is within
   * [snapThreshold] if it exists. Return null otherwise.
   */
  fun snapToDevice(
    @AndroidCoordinate px: Int,
    @AndroidCoordinate py: Int,
    snapThreshold: Int
  ): DeviceSize? {
    val reverse = px > py
    val x = if (reverse) py else px
    val y = if (reverse) px else py

    val toReturn = binarySearch(x, y, snapThreshold) ?: return null
    if (reverse) {
      // Here y <= x is possible.
      return toReturn.rotate()
    }
    return toReturn
  }

  private fun binarySearch(x: Int, y: Int, snapThreshold: Int): DeviceSize? {
    var start = 0
    var end = myList.size - 1

    // Find the closest point in the sorted list.
    while (start <= end) {

      val mid = (start + end) / 2
      val p = myList[mid]

      if (isInRange(snapThreshold, x, y, p.x, p.y)) {
        return p
      }

      if (p.x < x) {
        start = mid + 1
      } else {
        end = mid - 1
      }
    }
    return null
  }

  private fun isInRange(threshold: Int, x: Int, y: Int, px: Int, py: Int): Boolean {
    if (Math.abs(x - px) < threshold && Math.abs(y - py) < threshold) {
      return true
    }
    return false
  }
}
