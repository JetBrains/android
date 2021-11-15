/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.ddmlib.IDevice
import com.intellij.openapi.application.ApplicationNamesInfo

/**
 * Identifies a category of device in the connection assistant.
 */
enum class ConnectionAssistantSection { WORKING, POSSIBLE_PROBLEM, OTHER_USB };

/**
 * Summary of a device that is displayed in the connection assistant. Only contains fields that are actually displayed in the UI.
 */
data class DeviceSummary(val label: String, val device: IDevice?, val section: ConnectionAssistantSection, val errorMessage: String? = null)

/**
 * Compute a [DeviceSummary] containing the information to be displayed about a device in the Connection Assistant, given a
 * [DeviceCrossReference] containing all that is known about the device from various sources.
 */
fun summarize(crossReference: DeviceCrossReference): DeviceSummary {
  val device = crossReference.ddmsDevices.firstOrNull()

  var label: String
  if (device != null) {
    label = device.name.orEmpty()
  }
  else if (!crossReference.adbDevice.isEmpty()) {
    label = crossReference.adbDevice.first().serial.orEmpty()
  }
  else if (!crossReference.usbDevice.isEmpty()) {
    label = crossReference.usbDevice.first().name
  }
  else {
    label = "Unknown device"
  }

  var section: ConnectionAssistantSection
  var errorMessage: String? = null

  if (device != null) {
    val devState = device.state
    val adbDevice = crossReference.adbDevice.firstOrNull()
    if (adbDevice != null && adbDevice.state != devState) {
      section = ConnectionAssistantSection.POSSIBLE_PROBLEM
      errorMessage = "ADB reports that the device is in the '${adbDevice.state?.state ?: "unknown"}' state but ${ApplicationNamesInfo.getInstance().fullProductName} reports" +
                     " that it is in the '${devState?.state ?: "unknown"}' state"
    }
    else {
      when (devState) {
        IDevice.DeviceState.ONLINE -> {
          section = ConnectionAssistantSection.WORKING
        }
        IDevice.DeviceState.UNAUTHORIZED -> {
          section = ConnectionAssistantSection.POSSIBLE_PROBLEM
          errorMessage = "Device is waiting for you to grant permission for USB debugging"
        }
        else -> {
          section = ConnectionAssistantSection.POSSIBLE_PROBLEM
          errorMessage = "Device is currently in the ${device.state?.state ?: "unknown"} state"
        }
      }
    }
  }
  else if (!crossReference.adbDevice.isEmpty()) {
    section = ConnectionAssistantSection.POSSIBLE_PROBLEM
    errorMessage = "Device was detected by ADB but not ${ApplicationNamesInfo.getInstance().fullProductName}."
  }
  else {
    section = ConnectionAssistantSection.OTHER_USB
  }

  return DeviceSummary(label, device, section, errorMessage)
}