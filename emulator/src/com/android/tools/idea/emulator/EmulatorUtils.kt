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
package com.android.tools.idea.emulator

import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.KeyboardEvent.KeyEventType
import com.android.emulator.control.ThemingStyle
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.util.concurrency.SameThreadExecutor
import kotlinx.coroutines.cancelFutureOnCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Returns the emulator UI theme matching the current IDE theme.
 */
internal fun getEmulatorUiTheme(lafManager: LafManager): ThemingStyle.Style {
  val themeName = lafManager.currentLookAndFeel.name
  return when {
    themeName.contains("High contrast", ignoreCase = true) -> ThemingStyle.Style.CONTRAST
    themeName.contains("Light", ignoreCase = true) -> ThemingStyle.Style.LIGHT
    else -> ThemingStyle.Style.DARK // Darcula and custom themes that are based on Darcula.
  }
}

/**
 * Coroutine-friendly version of [ListenableFuture.get].
 */
suspend fun <T> ListenableFuture<T>.suspendingGet(): T {
  if (isDone) {
    @Suppress("BlockingMethodInNonBlockingContext")
    return get()
  }

  return suspendCancellableCoroutine { continuation ->
    continuation.cancelFutureOnCancellation(this)
    val listener = Runnable {
      val value = try {
        get()
      }
      catch (e: Throwable) {
        continuation.resumeWithException(e)
        return@Runnable
      }
      continuation.resume(value)
    }
    addListener(listener, SameThreadExecutor.INSTANCE)
  }
}

/**
 * Creates a [KeyboardEvent] for the given hardware key.
 * Key names are defined in https://developer.mozilla.org/en-US/docs/Web/API/KeyboardEvent/key/Key_Values.
 */
internal fun createHardwareKeyEvent(keyName: String, eventType: KeyEventType = KeyEventType.keypress): KeyboardEvent {
  return KeyboardEvent.newBuilder()
    .setKey(keyName)
    .setEventType(eventType)
    .build()
}

/**
 * Returns this integer scaled and rounded to the closest integer.
 *
 * @param scale the scale factor
 */
internal fun Int.scaled(scale: Double): Int =
  (this * scale).roundToInt()

/**
 * Returns this integer scaled and rounded down towards zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaledDown(scale: Double): Int =
  (this * scale).toInt()

/**
 * Returns this integer scaled and rounded up away from zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaledUp(scale: Double): Int =
  ceil(this * scale).roundToInt()

/**
 * Returns this [Dimension] scaled by the given factor.
 */
internal fun Dimension.scaled(scale: Double): Dimension {
  return if (scale == 1.0) this else Dimension(width.scaled(scale), height.scaled(scale))
}

/**
 * Returns this [Dimension] scaled independently along X and Y axes.
 */
internal fun Dimension.scaled(scaleX: Double, scaleY: Double): Dimension {
  return if (scaleX == 1.0 && scaleY == 1.0) this else Dimension(width.scaled(scaleX), height.scaled(scaleY))
}

/**
 * Returns this [Point] scaled by the given factor.
 */
internal fun Point.scaled(scale: Double): Point {
  return if (scale == 1.0) this else Point(x.scaled(scale), y.scaled(scale))
}

/**
 * Returns this integer scaled by multiplying by [numerator] and then dividing by [denominator].
 */
internal fun Int.scaledDown(numerator: Int, denominator: Int): Int =
  ((this.toLong() * numerator) / denominator).toInt()

/**
 * Converts this value from the `[0, fromRange-1]` interval to the `[0, toRange - 1]`interval by scaling by
 * the [toRange]/[fromRange] factor while maintaining symmetry with respect to the centers of the two intervals.
 *
 * The conversion is reversible, i.e. if `fromRange <= toRange`, then for every `i` in the `[0, fromRange-1]`
 * interval `i.scaledUnbiased(fromRange, toRange).scaledUnbiased(toRange, fromRange) = i`.
 */
internal fun Int.scaledUnbiased(fromRange: Int, toRange: Int): Int =
  ((this * 2L + 1) * toRange / (2 * fromRange)).toInt()

internal fun Point.scaledUnbiased(fromDim: Dimension, toDim: Dimension): Point =
  Point(x.scaledUnbiased(fromDim.width, toDim.width), y.scaledUnbiased(fromDim.height, toDim.height))

/**
 * Checks if the ratio between [width1] and [height1] is the same as the ratio between
 * [width2] and [height2] within the given relative [tolerance].
 */
internal fun isSameAspectRatio(width1: Int, height1: Int, width2: Int, height2: Int, tolerance: Double): Boolean {
  val a = width1.toDouble() * height2
  val b = width2.toDouble() * height1
  val d = a - b
  return abs(d) <= tolerance * abs(a + b) / 2
}

/**
 * Returns this [Dimension] rotated by [numQuadrants] quadrants.
 */
internal fun Dimension.rotatedByQuadrants(numQuadrants: Int): Dimension {
  return if (numQuadrants % 2 == 0) this else Dimension(height, width)
}

/**
 * Returns this [Point] rotated according to [rotation].
 */
internal fun Point.rotatedByQuadrants(rotation: Int): Point {
  return when (rotation and 0x3) { // True modulus
    1 -> Point(y, -x)
    2 -> Point(-x, -y)
    3 -> Point(-y, x)
    else -> this
  }
}

/**
 * Returns this Dimension if both its components are not greater than the [maximumValue], otherwise
 * returns this Dimension scaled down to satisfy this requirement while preserving the aspect ratio.
 */
internal fun Dimension.coerceAtMost(maximumValue: Dimension): Dimension {
  if (width <= maximumValue.width && height <= maximumValue.height) {
    return this
  }
  val scale = min(maximumValue.width.toDouble() / width, maximumValue.height.toDouble() / height).coerceAtMost(1.0)
  return Dimension(width.scaled(scale).coerceAtMost(maximumValue.width), height.scaled(scale).coerceAtMost(maximumValue.height))
}

internal val Container.sizeWithoutInsets: Dimension
  get() = Dimension(width - insets.left - insets.right.coerceAtLeast(0),
                    height - insets.top - insets.bottom.coerceAtLeast(0))

@Suppress("UnstableApiUsage")
val Project.earlyDisposable: Disposable
  get() = (this as? ProjectEx)?.earlyDisposable ?: this

internal fun Point.constrainInside(d: Dimension) =
  if (this in d) this else Point(x.coerceIn(0, d.width - 1), y.coerceIn(0, d.height - 1))

internal operator fun Dimension.contains(p: Point) = p.x in 0 until width && p.y in 0 until height

internal val Rectangle.right: Int
  get() = x + width

internal val Rectangle.bottom: Int
  get() = y + height

internal val MouseEvent.location: Point
  get() = Point(x, y)