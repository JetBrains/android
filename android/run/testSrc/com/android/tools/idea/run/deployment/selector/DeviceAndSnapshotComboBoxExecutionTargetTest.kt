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
package com.android.tools.idea.run.deployment.selector

import com.android.sdklib.deviceprovisioner.DeviceId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DeviceAndSnapshotComboBoxExecutionTargetTest {

  @Test
  fun canListDevicesInCustomChildDeviceAndSnapshotComboBoxExecutionTarget() {
    val deploymentTarget = mock<DeploymentTarget>()
    val deploymentTargetDevice = mock<DeploymentTargetDevice>()
    val devicesService = mock<DeploymentTargetDevicesService>()
    val deviceId = DeviceId("TEST", true, "")
    val deviceName = "test device name"

    whenever(deploymentTarget.deviceId).thenReturn(deviceId)
    whenever(deploymentTargetDevice.id).thenReturn(deviceId)
    whenever(deploymentTargetDevice.name).thenReturn(deviceName)
    whenever(devicesService.loadedDevicesOrNull()).thenReturn(listOf(deploymentTargetDevice))

    val customTarget = object : DeviceAndSnapshotComboBoxExecutionTarget(
      targets = listOf(deploymentTarget),
      devicesService = devicesService,
    ) {
      fun devicesNames(): List<String> = devices().map { it.name }
    }

    assertThat(customTarget.devicesNames()).isEqualTo(listOf(deviceName))
  }
}