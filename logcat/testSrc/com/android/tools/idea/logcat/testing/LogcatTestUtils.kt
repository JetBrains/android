/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.testing

import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB

/**
 * Convenience method for attaching multiple [TestDevice] in one call
 */
internal fun FakeAdbRule.attachDevices(vararg devices: TestDevice): List<DeviceState> = devices.map(::attachDevice)

/**
 * Convenience method for attaching a [TestDevice]
 */
internal fun FakeAdbRule.attachDevice(device: TestDevice): DeviceState {
  // Since we handle the response to the getprop commands ourselves, we don't really need to provide them to attachDevice
  val deviceState = attachDevice(device.serialNumber, manufacturer = "", model = "", release = "", sdk = "")
  if (!device.device.isOnline) {
    deviceState.deviceStatus = DeviceState.DeviceStatus.OFFLINE
  }
  return deviceState
}
