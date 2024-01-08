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
package com.android.tools.idea.wearwhs.communication

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import com.intellij.openapi.diagnostic.Logger

const val whsPackage: String = "com.google.android.wearable.healthservices"
const val whsUri: String = "content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config"

/**
 * Content provider implementation of [WearHealthServicesDeviceManager].
 *
 * This class uses the content provider for the synthetic HAL in WHS to sync the current state
 * of the UI to the selected Wear OS device.
 */
internal class ContentProviderDeviceManager(private val adbSession: AdbSession, private var capabilities: List<WhsCapability> = WHS_CAPABILITIES) : WearHealthServicesDeviceManager {
  private var serialNumber: String? = null
  private val logger = Logger.getInstance(ContentProviderDeviceManager::class.java)

  // TODO(b/309608749): Implement loadCapabilities method
  override suspend fun loadCapabilities() = capabilities

  // TODO(b/309607065): Implement loadCurrentCapabilityStates method
  override suspend fun loadCurrentCapabilityStates() = capabilities.associateWith {
    OnDeviceCapabilityState(false, null)
  }

  override suspend fun clearContentProvider() {
    if (serialNumber == null) {
      // TODO: Log this error
      return
    }

    val device = DeviceSelector.fromSerialNumber(serialNumber!!)
    adbSession.deviceServices.shellAsText(device, "content delete --uri $whsUri")
  }

  override fun setSerialNumber(serialNumber: String) {
    this.serialNumber = serialNumber
  }

  // TODO(b/305924111) Implement loadOngoingExercise method
  override suspend fun loadOngoingExercise() = false

  private fun contentUpdateMultipleCapabilities(capabilityUpdates: Map<WhsDataType, Boolean>): String {
    val sb = StringBuilder("content update --uri $whsUri")
    for (capabilityUpdate in capabilityUpdates.toSortedMap(compareBy { it.name })) {
      sb.append(" --bind ${capabilityUpdate.key}:b:${capabilityUpdate.value}")
    }
    return sb.toString()
  }

  private inline fun <reified T> contentUpdateCapability(key: String, value: T): String {
    val type = when (value) {
      is Boolean -> 'b'
      is Int -> 'i'
      is Float -> 'f'
      else -> 's'
    }
    return "content update --uri $whsUri --bind $key:$type:$value"
  }

  private suspend fun setCapability(capability: WhsCapability, newValue: Boolean) {
    if (serialNumber == null) {
      // TODO: Log this error
      return
    }

    val device = DeviceSelector.fromSerialNumber(serialNumber!!)
    val contentUpdateCommand = contentUpdateCapability(capability.key.name, newValue)
    adbSession.deviceServices.shellAsText(device, contentUpdateCommand)
  }

  override suspend fun enableCapability(capability: WhsCapability) {
    setCapability(capability, true)
  }

  override suspend fun disableCapability(capability: WhsCapability) {
    setCapability(capability, false)
  }

  override suspend fun setCapabilities(capabilityUpdates: Map<WhsDataType, Boolean>) {
    if (serialNumber == null) {
      // TODO: Log this error
      return
    }

    val contentUpdateCommand = contentUpdateMultipleCapabilities(capabilityUpdates)
    val device = DeviceSelector.fromSerialNumber(serialNumber!!)

    adbSession.deviceServices.shellAsText(device, contentUpdateCommand)
  }

  override suspend fun overrideValue(capability: WhsCapability, value: Number?) {
    if (serialNumber == null) {
      // TODO: Log this error
      return
    }

    val device = DeviceSelector.fromSerialNumber(serialNumber!!)

    val contentUpdateCommand = if (value == null) {
      contentUpdateCapability(capability.key.name, "\"\"")
    } else if (capability.key == WhsDataType.STEPS) {
      contentUpdateCapability(capability.key.name, value.toInt())
    } else {
      contentUpdateCapability(capability.key.name, value.toFloat())
    }
    adbSession.deviceServices.shellAsText(device, contentUpdateCommand)
  }

  override suspend fun triggerEvent(eventTrigger: EventTrigger) {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return
    }

    val device = DeviceSelector.fromSerialNumber(serialNumber!!)
    adbSession.deviceServices.shellAsText(device, triggerEventCommand(eventTrigger))
  }

  private fun triggerEventCommand(eventTrigger: EventTrigger) =
    "am broadcast -a \"${eventTrigger.eventKey}\" $whsPackage"
}
