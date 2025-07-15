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

import com.android.tools.idea.adb.wireless.provisioner.WifiPairableDeviceProvisionerPlugin.WifiPairableDeviceHandle
import com.android.tools.idea.adb.wireless.v2.ui.WifiPairableDevicesPersistentStateComponent
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class HideDeviceAction : DumbAwareAction("Hide", "Hide from device manager", null) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.deviceHandle() is WifiPairableDeviceHandle
  }

  override fun actionPerformed(e: AnActionEvent) {
    WifiPairableDevicesPersistentStateComponent.getInstance()
      .addHiddenDevice((e.deviceHandle() as WifiPairableDeviceHandle).serviceName)
  }
}
