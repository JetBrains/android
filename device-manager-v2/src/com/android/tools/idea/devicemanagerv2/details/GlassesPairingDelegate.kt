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
import com.android.tools.idea.devicemanagerv2.glassesPairedDevicesFlow
import com.android.tools.idea.deviceprovisioner.GlassesInteractivePairableDeviceHandle
import com.android.tools.idea.deviceprovisioner.runCatchingDeviceActionException
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import java.awt.Component
import kotlinx.coroutines.flow.Flow

class GlassesPairingDelegate(private val project: Project?, private val devicesFlow: Flow<List<DeviceHandle>>) : PairingDelegate {
  override val addMenuItemTitle: String
    get() = DeviceManagerBundle.message("pairedDevices.add.glasses")

  override fun getPairingId(handle: DeviceHandle): String? = handle.id.toString()

  override fun isDelegateForDevice(handle: DeviceHandle, pairedDevice: DeviceHandle): Boolean =
    handle.state.properties.deviceType == DeviceType.AI_GLASSES || pairedDevice.state.properties.deviceType == DeviceType.AI_GLASSES

  override fun isPairDeviceWizardSupported(handle: DeviceHandle): Boolean {
    val properties = handle.state.properties
    return when (properties.deviceType) {
      DeviceType.AI_GLASSES -> handle is GlassesInteractivePairableDeviceHandle && handle.isPairGlassesEnabled()
      DeviceType.HANDHELD ->
        StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.get() &&
          (handle as? GlassesInteractivePairableDeviceHandle)?.isPairGlassesEnabled() == true
      else -> false
    }
  }

  override suspend fun showPairDeviceWizard(parent: Component, handle: DeviceHandle) {
    val interactiveHandle = handle as? GlassesInteractivePairableDeviceHandle ?: return
    interactiveHandle.pairGlasses(parent, project)
  }

  override fun getRemovePresentation(handle: DeviceHandle, pairedDevice: DeviceHandle): RemovePresentation {
    val glassesHandle = getGlassesHandle(handle, pairedDevice)
    val isEnabled = glassesHandle?.isUnpairGlassesEnabled() == true
    val (glasses, phone) = getGlassesAndPhone(handle, pairedDevice)
    val title = DeviceManagerBundle.message("glasses.pairing.unpair.confirmation.title", glasses.state.properties.title)
    val message =
      DeviceManagerBundle.message(
        "glasses.pairing.unpair.confirmation.message",
        glasses.state.properties.title,
        phone.state.properties.title,
      )
    return RemovePresentation(isEnabled = isEnabled, confirmationTitle = title, confirmationMessage = message)
  }

  override suspend fun removeDevice(parent: Component?, handle: DeviceHandle, pairedDevice: DeviceHandle) {
    val glassesHandle = getGlassesHandle(handle, pairedDevice) ?: return
    runCatchingDeviceActionException(project, glassesHandle.state.properties.title) { glassesHandle.unpairGlasses(parent) }
  }

  override fun pairedDevicesFlow(handle: DeviceHandle, devicesFlow: Flow<List<DeviceHandle>>): Flow<Map<String, List<PairingStatus>>> =
    devicesFlow.glassesPairedDevicesFlow()

  private fun getGlassesAndPhone(handle: DeviceHandle, pairedDevice: DeviceHandle): Pair<DeviceHandle, DeviceHandle> =
    if (handle.state.properties.deviceType == DeviceType.AI_GLASSES) {
      handle to pairedDevice
    } else {
      pairedDevice to handle
    }

  private fun getGlassesHandle(handle: DeviceHandle, pairedDevice: DeviceHandle): GlassesInteractivePairableDeviceHandle? {
    val (glasses, _) = getGlassesAndPhone(handle, pairedDevice)
    return glasses as? GlassesInteractivePairableDeviceHandle
  }
}
