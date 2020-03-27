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
import com.android.tools.idea.npw.assetstudio.roundToInt
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlin.math.ceil

/**
 * Invokes given function on the UI thread regardless of the modality state.
 */
internal fun invokeLater(@UiThread action: () -> Unit) {
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
 * True if the rotation is 90 degrees clockwise or counterclockwise.
 */
internal inline val SkinRotation.is90Degrees
  get() = ordinal % 2 != 0

/**
 * Returns the rotation that is that is less than this one by 90 degrees.
 */
internal fun SkinRotation.decrementedBy90Degrees(): SkinRotation =
  SkinRotation.forNumber((ordinal + 3) % 4)

/**
 * Returns this integer scaled and rounded to the closest integer.
 *
 * @param scale the scale factor
 */
internal fun Int.scale(scale: Double): Int =
  (this * scale).roundToInt()

/**
 * Returns this integer scaled and rounded down towards zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaleDown(scale: Double): Int =
  (this * scale).toInt()

/**
 * Returns this integer scaled and rounded up away from zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaleUp(scale: Double): Int =
  ceil(this * scale).roundToInt()
