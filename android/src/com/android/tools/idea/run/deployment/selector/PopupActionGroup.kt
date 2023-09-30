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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction
import com.android.tools.idea.run.deployment.Heading
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator

internal class PopupActionGroup(
  private val devices: Collection<Device>,
  private val comboBoxAction: DeviceAndSnapshotComboBoxAction,
  private val actionManager: ActionManager = ActionManager.getInstance()
) : DefaultActionGroup() {
  init {
    val deviceActions = newSelectDeviceActionsOrSnapshotActionGroups()
    addAll(deviceActions)
    if (!deviceActions.isEmpty()) {
      addSeparator()
    }
    add(actionManager.getAction(SelectMultipleDevicesAction.ID))
    add(actionManager.getAction(PairDevicesUsingWiFiAction.ID))
    add(actionManager.getAction("Android.DeviceManager"))
    actionManager.getAction("DeveloperServices.ConnectionAssistant")?.let {
      addSeparator()
      add(it)
    }
  }

  private fun newSelectDeviceActionsOrSnapshotActionGroups(): Collection<AnAction> {
    val size = devices.size
    val runningDevices = ArrayList<Device>(size)
    val availableDevices = ArrayList<Device>(size)
    for (device in devices) {
      when {
        device.isConnected -> runningDevices.add(device)
        else -> availableDevices.add(device)
      }
    }
    val actions = ArrayList<AnAction>(3 + size)
    if (runningDevices.isNotEmpty()) {
      actions.add(actionManager.getAction(Heading.RUNNING_DEVICES_ID))
    }
    for (runningDevice in runningDevices) {
      actions.add(SelectDeviceAction(runningDevice, comboBoxAction))
    }
    if (runningDevices.isNotEmpty() && availableDevices.isNotEmpty()) {
      actions.add(Separator.create())
    }
    if (availableDevices.isNotEmpty()) {
      actions.add(actionManager.getAction(Heading.AVAILABLE_DEVICES_ID))
    }
    for (availableDevice in availableDevices) {
      actions.add(
        when {
          availableDevice.snapshots.isNotEmpty() ->
            SnapshotActionGroup(availableDevice, comboBoxAction)
          else -> SelectDeviceAction(availableDevice, comboBoxAction)
        }
      )
    }
    return actions
  }
}
