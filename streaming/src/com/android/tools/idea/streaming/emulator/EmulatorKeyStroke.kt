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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.KeyboardEvent.KeyEventType
import java.awt.event.InputEvent.ALT_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK

/** Defines a sequence of [KeyboardEvent]s. */
internal data class EmulatorKeyStroke(val keyName: String, val modifiers: Int = 0)

/** Simulates pressing of Emulator keys corresponding to the given [modifiers]. */
internal fun EmulatorController.pressModifierKeys(modifiers: Int) {
  if (modifiers != 0) {
    var currentModifiers = 0
    val event = KeyboardEvent.newBuilder().setEventType(KeyEventType.keydown)
    for ((modifier, key) in EMULATOR_MODIFIER_KEYS) {
      if ((modifiers and modifier) != 0) {
        currentModifiers = currentModifiers or modifier
        sendKey(event.setKey(key).build())
        if (currentModifiers == modifiers) {
          break
        }
      }
    }
  }
}

/** Simulates releasing of Emulator keys corresponding to the given [modifiers]. */
internal fun EmulatorController.releaseModifierKeys(modifiers: Int) {
  if (modifiers != 0) {
    // Simulate releasing of meta keys.
    var currentModifiers = modifiers
    val event = KeyboardEvent.newBuilder().setEventType(KeyEventType.keyup)
    for ((modifier, key) in EMULATOR_MODIFIER_KEYS.asReversed()) {
      if ((currentModifiers and modifier) != 0) {
        currentModifiers = currentModifiers and modifier.inv()
        sendKey(event.setKey(key).build())
        if (currentModifiers == 0) {
          break
        }
      }
    }
  }
}

/** Modifiers and their corresponding Emulator key names. */
private val EMULATOR_MODIFIER_KEYS = listOf(
  Pair(ALT_DOWN_MASK, "Alt"),
  Pair(SHIFT_DOWN_MASK, "Shift"),
  Pair(CTRL_DOWN_MASK, "Control"),
  Pair(META_DOWN_MASK, "Meta"),
)