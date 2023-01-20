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
package com.android.tools.idea.logcat.devices

import com.android.adblib.DeviceSelector
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import org.jetbrains.annotations.VisibleForTesting

/**
 * Implementation of [DeviceFinder] that finds a connected device using a  [com.android.sdklib.deviceprovisioner.DeviceProvisioner]
 */
internal class ConnectedDeviceFinder @NonInjectable @VisibleForTesting constructor(
  private val deviceProvisioner: DeviceProvisioner,
) : DeviceFinder {

  @Suppress("unused") // Used by system in create a project service
  constructor(project: Project) : this(project.service<DeviceProvisionerService>().deviceProvisioner)

  override suspend fun findDevice(serialNumber: String): Device? =
    deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))?.state?.toDevice()
}
