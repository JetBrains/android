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
package com.android.tools.profilers.perfetto.traceprocessor

import kotlin.math.abs

object TraceProcessorModelUtils {
  /**
   * Finds the value associated with a key in a LinkedHashMap with tolerance.
   *
   * This function performs a binary search with tolerance on the keys of the provided LinkedHashMap to find a key close to the specified
   * target key. If a key within the tolerance is found, the corresponding value in the map is returned. If the target key is null, the
   * function returns null.
   *
   * @param sortedMap The LinkedHashMap containing key-value pairs to search. It is expected to be sorted by its keys in increasing order.
   * @param targetKey The target key for which a close key is sought, or null to indicate no target.
   * @param tolerance The allowable difference between the target key and the found key.
   * @return The value associated with the key found within tolerance, or null if target key is null or no key within tolerance is found.
   */
  fun findValueNearKey(sortedMap: LinkedHashMap<Long, Int>, targetKey: Long, tolerance: Long): Int? {
    val valueNearKey = binarySearchWithTolerance(sortedMap.keys.toList(), targetKey, tolerance)
    return valueNearKey?.let { sortedMap[it] }
  }

  private fun binarySearchWithTolerance(keys: List<Long>, target: Long, tolerance: Long): Long? {
    var low = 0
    var high = keys.size - 1

    while (low <= high) {
      val mid = (low + high) / 2
      val midValue = keys[mid]

      if (abs(midValue - target) <= tolerance) {
        return keys[mid] // Found a value within the tolerance
      }

      if (midValue < target) {
        low = mid + 1
      } else {
        high = mid - 1
      }
    }

    // If no exact match is found, null is returned.
    return null
  }
}