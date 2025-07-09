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
package com.android.tools.idea.backup

import com.android.adblib.DeviceSelector
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting

internal class DeviceCheckerImpl
@VisibleForTesting
constructor(private val deviceProvisioner: DeviceProvisioner) : DeviceChecker {
  constructor(
    project: Project
  ) : this(project.service<DeviceProvisionerService>().deviceProvisioner)

  override suspend fun checkDevice(serialNumber: String): String? {
    val deviceHandle =
      deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))
        ?: return "Device not found"
    val properties = deviceHandle.state.properties
    val apiLevel = properties.androidVersion?.androidApiLevel?.majorVersion ?: 0
    if (apiLevel < 31) {
      return "Device API level $apiLevel is not supported"
    }

    return when (val deviceType = properties.deviceType) {
      null -> "Unable to determine device type"
      DeviceType.HANDHELD -> null
      else -> "Device of type ${deviceType.stringValue} is not supported"
    }
  }
}
