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

import com.android.tools.idea.streaming.emulator.EmulatorController
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.ktor.util.collections.ConcurrentMap

/**
 * Orchestrates mouse and keyboard input for XR devices. Thread safe.
 */
internal class EmulatorXrInputController(private val emulator: EmulatorController): Disposable {

  @Volatile var inputMode: XrInputMode = XrInputMode.HAND

  init {
    Disposer.register(emulator, this)
  }

  override fun dispose() {
  }

  companion object {
    fun getInstance(project: Project, emulator: EmulatorController): EmulatorXrInputController =
        project.service<EmulatorXrInputControllerService>().getXrInputController(emulator)
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

@Service(Service.Level.PROJECT)
internal class EmulatorXrInputControllerService: Disposable {

  private val xrControllers = ConcurrentMap<EmulatorController, EmulatorXrInputController>()

  fun getXrInputController(emulator: EmulatorController): EmulatorXrInputController {
    return xrControllers.computeIfAbsent(emulator) {
      Disposer.register(emulator) {
        xrControllers.remove(emulator)
      }
      return@computeIfAbsent EmulatorXrInputController(emulator)
    }
  }

  override fun dispose() {
    xrControllers.clear()
  }
}