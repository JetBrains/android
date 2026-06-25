/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2.details

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.devicemanagerv2.DeviceManagerBundle
import com.android.tools.idea.devicemanagerv2.PairingStatus
import com.android.tools.idea.devicemanagerv2.pairedDevicesFlow
import com.android.tools.idea.wearpairing.WearDevicePairingWizard
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.openapi.project.Project
import java.awt.Component
import kotlinx.coroutines.flow.Flow

class WearPairingDelegate(private val project: Project?) : PairingDelegate {
  override val addMenuItemTitle: String
    get() = DeviceManagerBundle.message("pairedDevices.add.wear")

  override fun getPairingId(handle: DeviceHandle): String? = handle.state.properties.wearPairingId

  override fun isDelegateForDevice(handle: DeviceHandle, pairedDevice: DeviceHandle): Boolean =
    pairedDevice.state.properties.wearPairingId != null || pairedDevice.state.properties.deviceType == DeviceType.WEAR

  override fun isPairDeviceWizardSupported(handle: DeviceHandle): Boolean {
    val properties = handle.state.properties
    val isPhysicalWear = handle.state.isOnline() && properties.deviceType == DeviceType.WEAR && properties.isVirtual != true
    return properties.wearPairingId != null && !isPhysicalWear
  }

  override suspend fun showPairDeviceWizard(parent: Component, handle: DeviceHandle) {
    val pairingId = getPairingId(handle) ?: return
    WearDevicePairingWizard().show(project, pairingId)
  }

  override fun getRemovePresentation(handle: DeviceHandle, pairedDevice: DeviceHandle): RemovePresentation {
    val (phone, wear) = getPhoneAndWear(handle, pairedDevice)
    val title = DeviceManagerBundle.message("pairedDevices.remove.title", wear.state.properties.title, phone.state.properties.title)
    val message = DeviceManagerBundle.message("pairedDevices.remove.message", wear.state.properties.title, phone.state.properties.title)
    return RemovePresentation(isEnabled = true, confirmationTitle = title, confirmationMessage = message)
  }

  override suspend fun removeDevice(parent: Component?, handle: DeviceHandle, pairedDevice: DeviceHandle) {
    val (phone, wear) = getPhoneAndWear(handle, pairedDevice)
    val phonePairingId = getPairingId(phone) ?: return
    val wearPairingId = getPairingId(wear) ?: return
    WearPairingManager.getInstance().removePairedDevices(phonePairingId, wearPairingId, true)
  }

  override fun pairedDevicesFlow(handle: DeviceHandle, devicesFlow: Flow<List<DeviceHandle>>): Flow<Map<String, List<PairingStatus>>> =
    WearPairingManager.getInstance().pairedDevicesFlow()

  private fun getPhoneAndWear(handle: DeviceHandle, pairedDevice: DeviceHandle): Pair<DeviceHandle, DeviceHandle> =
    if (pairedDevice.state.properties.deviceType == DeviceType.WEAR) {
      handle to pairedDevice
    } else {
      pairedDevice to handle
    }
}
