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

import com.android.tools.adtui.model.Range
import java.util.concurrent.TimeUnit.MICROSECONDS
import studio.network.inspection.NetworkInspectorProtocol.Event

/**
 * Find the index of the first event with `timestamp >= minNs
 *
 * The `binarySearch` function has a different behavior depending on whether the item is found or
 * not.
 *
 * If the item is found, the result is an index of an arbitrary matching value (in case there's more
 * than 1). If the item is not found, the result is the inverted insertion point.
 *
 * We can "trick" `binarySearch` to always return the inverted insertion point by returning `1` if
 * the item is a match. This forces `binarySearch` to keep looking.
 */
internal fun List<Event>.findStartIndex(minNs: Long): Int =
  // `pos` is an `inverted insertion point`. Convert back to insertion point.
  binarySearch { pos -> pos.timestamp.compareTo(minNs).takeIf { it != 0 } ?: 1 }.inv()

/**
 * Find the index of the last event with `timestamp <= maxNs
 *
 * The `binarySearch` function has a different behavior depending on whether the item is found or
 * not.
 *
 * If the item is found, the result is an index of an arbitrary matching value (in case there's more
 * than 1). If the item is not found, the result is the inverted insertion point (-insertion point -
 * 1).
 *
 * We can "trick" `binarySearch` to always return the inverted insertion point by returning `-1` if
 * the item is a match. This forces `binarySearch` to keep looking.
 */
internal fun List<Event>.findEndIndex(maxNs: Long): Int =
  // `pos` is an `inverted insertion point`. Convert back to insertion point and subtract 1.
  binarySearch { pos -> pos.timestamp.compareTo(maxNs).takeIf { it != 0 } ?: -1 }.inv() - 1

/**
 * Return all events that fall within [range] inclusive.
 *
 * This function is designed to be fast (logN) because it gets called frequently by the frontend.
 */
internal fun List<Event>.searchRange(range: Range): List<Event> {
  val min = MICROSECONDS.toNanos(range.min.toLong())
  val max = MICROSECONDS.toNanos(range.max.toLong())
  val startIndex = findStartIndex(min)
  val endIndex = findEndIndex(max)
  return slice(startIndex..endIndex)
}
