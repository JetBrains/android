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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.devicemanagerv2.DeviceManagerUsageTracker.logDeviceManagerEvent
import com.android.tools.idea.wearpairing.WearDevicePairingWizard
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.PHYSICAL_PAIR_DEVICE_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.PHYSICAL_UNPAIR_DEVICE_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_PAIR_DEVICE_ACTION
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind.VIRTUAL_UNPAIR_DEVICE_ACTION
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.launch
import org.jetbrains.android.AndroidPluginDisposable

class PairWearableDeviceAction : DumbAwareAction("Pair Wearable") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val deviceRowData = e.deviceRowData()
    if (deviceRowData?.wearPairingId == null || deviceRowData.isNonEmulatorWearDevice()) {
      // disabled if the device doesn't have a pairing ID or the device is a Wear device and is not
      // an emulator as we only support pairing to a Wear Emulator.
      e.presentation.isEnabledAndVisible = false
    } else {
      // Visible if the device supports pairing
      e.presentation.isVisible = true
      // Enabled if the device can be paired at the moment: it's online, or we can make it be online
      e.presentation.isEnabled =
        deviceRowData.status == DeviceRowData.Status.ONLINE ||
          deviceRowData.handle?.activationAction?.presentation?.value?.enabled == true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceRowData = DEVICE_ROW_DATA_KEY.getData(e.dataContext) ?: return

    logDeviceManagerEvent(
      when {
        deviceRowData.isVirtual -> VIRTUAL_PAIR_DEVICE_ACTION
        else -> PHYSICAL_PAIR_DEVICE_ACTION
      }
    )

    WearDevicePairingWizard().show(CommonDataKeys.PROJECT.getData(e.dataContext), wearPairingId(e))
  }
}

private fun DeviceRowData.isNonEmulatorWearDevice() = !isVirtual && type == DeviceType.WEAR

class ViewPairedDevicesAction : DumbAwareAction("View Paired Device(s)") {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.updatePairedDeviceActionPresentation()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val row = e.deviceRowData() ?: return
    e.deviceManagerPanel()?.showPairedDevices(row)
  }
}

class UnpairWearableDeviceAction() : DumbAwareAction("Unpair Device") {
  // The WearPairingManager is an Application-scoped service, so we use that scope too.
  private val coroutineScope =
    AndroidCoroutineScope(AndroidPluginDisposable.getApplicationInstance())

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.updatePairedDeviceActionPresentation()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deviceRowData = DEVICE_ROW_DATA_KEY.getData(e.dataContext) ?: return
    val wearPairingId = deviceRowData.wearPairingId ?: return

    logDeviceManagerEvent(
      when {
        deviceRowData.isVirtual -> VIRTUAL_UNPAIR_DEVICE_ACTION
        else -> PHYSICAL_UNPAIR_DEVICE_ACTION
      }
    )

    coroutineScope.launch {
      WearPairingManager.getInstance().removeAllPairedDevices(wearPairingId, true)
    }
  }
}

/**
 * Updates the presentation for actions that involve a paired device: invisible if the device
 * doesn't support pairing at all or is unpaired, and enabled if the device is paired.
 */
private fun AnActionEvent.updatePairedDeviceActionPresentation() {
  presentation.isEnabledAndVisible =
    when (val wearPairingId = wearPairingId(this)) {
      null -> false
      else -> WearPairingManager.getInstance().getPairsForDevice(wearPairingId).isNotEmpty()
    }
}

private fun wearPairingId(e: AnActionEvent): String? =
  DEVICE_ROW_DATA_KEY.getData(e.dataContext)?.wearPairingId
