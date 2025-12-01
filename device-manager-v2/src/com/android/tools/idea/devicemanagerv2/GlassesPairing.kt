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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** Launches the Glasses Pairing wizard. */
class PairGlassesAction() : DumbAwareAction("Pair Glasses") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (
      StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.get() &&
        e.deviceHandle()?.state?.properties?.deviceType == DeviceType.AI_GLASSES
    ) {
      e.updateFromDeviceAction(DeviceHandle::pairGlassesAction)
    } else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceHandle = e.deviceHandle()
    val pairGlassesAction = deviceHandle?.pairGlassesAction ?: return

    deviceHandle.launchCatchingDeviceActionException(project = e.project) {
      // TODO android merge : removed second arg deviceHandle
      pairGlassesAction.pairGlasses(e.componentToRestoreFocusTo())
    }
  }
}

/** Unpairs the glasses from a companion device. */
class UnpairGlassesAction() : DumbAwareAction("Unpair Glasses") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    // TODO(b/458470193): Implement unpairing
    e.presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceHandle = e.deviceHandle()
    val pairGlassesAction = deviceHandle?.unpairGlassesAction ?: return

    deviceHandle.launchCatchingDeviceActionException(project = e.project) {
      pairGlassesAction.unpairGlasses()
    }
  }
}
