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
package com.android.tools.idea.streaming.emulator.xr

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData

private val EMULATOR_XR_INPUT_CONTROLLER_KEY = Key.create<EmulatorXrInputController>("EmulatorXrInputController")

@UiThread
internal class EmulatorXrInputController private constructor(private val emulator: EmulatorController) {

  var inputMode: XrInputMode = XrInputMode.HAND

  companion object {
    fun getInstance(emulator: EmulatorController): EmulatorXrInputController =
        emulator.getOrCreateUserData(EMULATOR_XR_INPUT_CONTROLLER_KEY) { EmulatorXrInputController(emulator) }
  }
}

internal enum class XrInputMode {
  /** Mouse is used to interact with running apps simulating hand tracking. */
  HAND,
  /** Mouse is used to interact with running apps simulating eye tracking. */
  EYE,
  /** Mouse and keyboard events are transparently forwarded to the device. */
  HARDWARE,
  /** Relative mouse coordinates control view direction. */
  VIEW_DIRECTION,
  /** Relative mouse coordinates control location in x-y plane. Mouse wheel controls moving forward and back. */
  LOCATION_IN_SPACE_XY,
  /** Relative mouse y coordinate controls moving forward and back. */
  LOCATION_IN_SPACE_Z,
}