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
package com.android.tools.idea.device.explorer.files.mocks

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.explorer.files.external.services.DeviceFileSystemManager
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.flow.MutableStateFlow

class MockDeviceFileSystemManager : DeviceFileSystemManager {
  private val edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
  private val devices = MutableStateFlow(emptyList<DeviceFileSystem>())

  fun addDevice(deviceName: String): MockDeviceFileSystem {
    val device = MockDeviceFileSystem(edtExecutor, deviceName)
    devices.value += device
    return device
  }

  fun removeDevice(device: MockDeviceFileSystem): Boolean {
    val removed = devices.value.contains(device)
    devices.value -= device
    return removed
  }

  override suspend fun getFileSystem(serialNumber: String): DeviceFileSystem? {
    return devices.value.find { it.name == serialNumber }
  }
}