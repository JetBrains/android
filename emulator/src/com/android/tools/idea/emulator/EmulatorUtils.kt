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

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.KeyboardEvent.KeyEventType
import com.android.emulator.control.Rotation.SkinRotation
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Invokes given function on the UI thread regardless of the modality state.
 */
internal fun invokeLaterInAnyModalityState(@UiThread action: () -> Unit) {
  ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
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
 * Returns this [Dimension] rotated according to [rotation].
 */
internal fun Dimension.rotated(rotation: SkinRotation): Dimension {
  return if (rotation == SkinRotation.LANDSCAPE || rotation == SkinRotation.REVERSE_LANDSCAPE) Dimension(height, width) else this
}

/**
 * Returns this [Point] rotated according to [rotation].
 */
internal fun Point.rotated(rotation: SkinRotation): Point {
  return when (rotation) {
    SkinRotation.LANDSCAPE -> Point(y, -x)
    SkinRotation.REVERSE_PORTRAIT -> Point(-x, -y)
    SkinRotation.REVERSE_LANDSCAPE -> Point(-y, x)
    else -> this
  }
}

internal val JPanel.sizeWithoutInsets: Dimension
  get() = Dimension(width - insets.left - insets.right, height - insets.top - insets.bottom)

val Project.earlyDisposable: Disposable
  get() = (this as? ProjectEx)?.earlyDisposable ?: this