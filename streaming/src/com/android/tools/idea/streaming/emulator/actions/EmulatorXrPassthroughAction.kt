/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.XrOptions
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.xr.EmulatorXrInputController
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/** Toggles passthrough for a virtual XR headset. */
class EmulatorXrPassthroughAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val xrController = getEmulatorXrInputController(event) ?: return false
    return xrController.passthroughCoefficient > 0
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    val emulator = getEmulatorController(event) ?: return
    val project = event.project ?: return
    val xrController = EmulatorXrInputController.getInstance(project, emulator)
    val passthroughCoefficient = if (state) 1f else 0f
    val xrOptions =
        XrOptions.newBuilder().setPassthroughCoefficient(passthroughCoefficient).setEnvironment(xrController.environment).build()
    emulator.setXrOptions(xrOptions, object : EmptyStreamObserver<Empty>() {
      override fun onNext(message: Empty) {
        xrController.passthroughCoefficient = passthroughCoefficient
      }
    })
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    presentation.isVisible = getEmulatorConfig(event)?.deviceType == DeviceType.XR
    presentation.isEnabled = isEnabled(event)
  }

  private fun isEnabled(event: AnActionEvent): Boolean {
    val xrController = getEmulatorXrInputController(event) ?: return false
    return xrController.passthroughCoefficient != EmulatorXrInputController.UNKNOWN_PASSTHROUGH_COEFFICIENT
  }
}
