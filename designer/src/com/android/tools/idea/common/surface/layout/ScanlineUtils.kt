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
package com.android.tools.idea.common.surface.layout

import com.android.tools.idea.common.layout.positionable.PositionableContent

/*
 * Scanlines are a simplified method to find the maximum allowed area that a [SceneView] can paint to. We simply build
 * a grid using the top, left, bottom and right coordinates. When we want to find the maximum point where we can paint
 * we just find the first top and left coordinates that are after our the currently being painted SceneView bottom and
 * right.
 *
 * For example:
 *   0
 * 0 +-----+ +---+ +-------+
 *   | A   | | B | | C     |
 *   |     | |   | |       |
 *   |     | +---+ +-------+
 *   +-----+
 *   +------------+
 *   | D          |
 *   |            |
 *   +------------+
 *
 * The scan lines are:
 * top scanlines: Top of A, Top of B, ....
 * bottom scanlines: Bottom of A, Bottom of B....
 * etc
 *
 * If we want to find the max drawable area for B, we will find in the scanlines, the right side of A and the top of the
 * drawable area (since there is nothing on top of B).
 * We will also find the top side of D and the left side of C.
 */

/** A list of scanline coordinates. */
typealias ScanlineList = List<Int>

/**
 * Maps the [Collection<SceneView>] into a [ScanlineList] by applying the given function. The
 * returned list will be sorted.
 */
fun Collection<PositionableContent>.findAllScanlines(
  dimensionProcessor: (PositionableContent) -> Int
): ScanlineList = map(dimensionProcessor).sorted()

/**
 * Finds the closest scanline to [key] that is smaller or equals to key. If there is no smaller
 * scanline than key, the method returns [default].
 */
fun findSmallerScanline(scanLines: ScanlineList, key: Int, default: Int): Int {
  val index = scanLines.binarySearch(key)
  if (index < 0) {
    // The element is not present so binarySearch returned the insertion point. The smaller item is
    // the one before
    // the insertion point.
    val insertionIndex = -index - 1
    return if (insertionIndex - 1 < 0) default else scanLines[insertionIndex - 1]
  }

  return scanLines[index]
}

/**
 * Finds the closest scanline to [key] that is larger or equals to key. If there is no larger
 * scanline than key, the method returns [default].
 */
fun findLargerScanline(scanLines: ScanlineList, key: Int, default: Int): Int {
  val index = scanLines.binarySearch(key)
  if (index < 0) {
    // The element is not present so binarySearch returned the insertion point. The larger item is
    // the one at
    // the insertion point.
    val insertionIndex = -index - 1
    return if (insertionIndex >= scanLines.size) default else scanLines[insertionIndex]
  }

  return scanLines[index]
}
