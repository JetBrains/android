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
package com.android.tools.adtui.util

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.roundToInt

/** Returns this integer multiplied by [scale] and rounded to the closest integer. */
fun Int.scaled(scale: Double): Int =
    (this * scale).roundToInt()

/** Returns this [Dimension] scaled by the given factor. */
fun Dimension.scaled(scale: Double): Dimension =
    if (scale == 1.0) this else Dimension(width.scaled(scale), height.scaled(scale))

/** Returns this [Dimension] scaled independently along X and Y axes. */
fun Dimension.scaled(scaleX: Double, scaleY: Double): Dimension =
    if (scaleX == 1.0 && scaleY == 1.0) this else Dimension(width.scaled(scaleX), height.scaled(scaleY))

/** Returns this [Rectangle] scaled independently along X and Y axes. */
fun Rectangle.scaled(scaleX: Double, scaleY: Double): Rectangle =
    if (scaleX == 1.0 && scaleY == 1.0) this else Rectangle(x.scaled(scaleX), y.scaled(scaleY), width.scaled(scaleX), height.scaled(scaleY))

/** Returns this [Point] scaled by the given factor. */
fun Point.scaled(scale: Double): Point =
   if (scale == 1.0) this else Point(x.scaled(scale), y.scaled(scale))

/** Returns this [Dimension] rotated by [numQuadrants] quadrants. */
fun Dimension.rotatedByQuadrants(numQuadrants: Int): Dimension =
  if (numQuadrants % 2 == 0) this else Dimension(height, width)

/** Returns this [Point] rotated according to [numQuadrants]. */
fun Point.rotatedByQuadrants(numQuadrants: Int): Point {
  return when (normalizedRotation(numQuadrants)) {
    1 -> Point(y, -x)
    2 -> Point(-x, -y)
    3 -> Point(-y, x)
    else -> this
  }
}

fun normalizedRotation(rotation: Int) =
    rotation and 0x3
