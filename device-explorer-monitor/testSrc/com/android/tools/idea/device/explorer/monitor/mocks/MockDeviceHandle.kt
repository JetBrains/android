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
package com.android.tools.idea.device.explorer.monitor.mocks

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.testutils.MockitoKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockDeviceHandle(override val scope: CoroutineScope, val serialNumber: String) : DeviceHandle {
  override val id = DeviceId("TEST", false, "")
  private val mockDeviceState = mock<DeviceState.Connected>()
  private val mockConnectedDevice = mock<ConnectedDevice>()
  private val mockDeviceInfo = mock<DeviceInfo>()
  private val deviceInfoFlow: StateFlow<DeviceInfo> = MutableStateFlow(mockDeviceInfo)
  override val stateFlow: StateFlow<DeviceState> = MutableStateFlow(mockDeviceState)

  init {
    whenever(mockDeviceState.connectedDevice).thenReturn(mockConnectedDevice)
    whenever(mockConnectedDevice.deviceInfoFlow).thenReturn(deviceInfoFlow)
    whenever(mockDeviceInfo.serialNumber).thenReturn(serialNumber)
  }
}