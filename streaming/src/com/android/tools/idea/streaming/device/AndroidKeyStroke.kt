/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

/** Defines a sequence of [KeyEventMessage]s. */
data class AndroidKeyStroke(val keyCode: Int, val metaState: Int = 0)

/** Sends the given [keyStroke] to the emulator. */
fun DeviceController.sendKeyStroke(keyStroke: AndroidKeyStroke) {
  pressMetaKeys(keyStroke.metaState)
  sendControlMessage(KeyEventMessage(AndroidKeyEventActionType.ACTION_DOWN_AND_UP, keyStroke.keyCode, keyStroke.metaState))
  releaseMetaKeys(keyStroke.metaState)
}

/** Simulates pressing of meta keys corresponding to the given [metaState]. */
private fun DeviceController.pressMetaKeys(metaState: Int) {
  if (metaState != 0) {
    var currentMetaState = 0
    for ((key, state) in ANDROID_META_KEYS) {
      if ((metaState and state) != 0) {
        currentMetaState = currentMetaState or state
        sendControlMessage(KeyEventMessage(AndroidKeyEventActionType.ACTION_DOWN, key, currentMetaState))
        if (currentMetaState == metaState) {
          break
        }
      }
    }
  }
}

/** Simulates releasing of meta keys corresponding to the given [metaState]. */
private fun DeviceController.releaseMetaKeys(metaState: Int) {
  if (metaState != 0) {
    // Simulate releasing of meta keys.
    var currentMetaState = metaState
    for ((key, state) in ANDROID_META_KEYS.asReversed()) {
      if ((currentMetaState and state) != 0) {
        currentMetaState = currentMetaState and state.inv()
        sendControlMessage(KeyEventMessage(AndroidKeyEventActionType.ACTION_UP, key, currentMetaState))
        if (currentMetaState == 0) {
          break
        }
      }
    }
  }
}

/** Android meta keys and their corresponding meta states. */
private val ANDROID_META_KEYS = listOf(
  AndroidKeyStroke(AKEYCODE_ALT_LEFT, AMETA_ALT_ON),
  AndroidKeyStroke(AKEYCODE_SHIFT_LEFT, AMETA_SHIFT_ON),
  AndroidKeyStroke(AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON),
  AndroidKeyStroke(AKEYCODE_META_LEFT, AMETA_META_ON),
)

