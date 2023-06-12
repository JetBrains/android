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
package com.android.tools.idea.file.explorer.toolwindow.mocks

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystem
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystemService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executor

const val OPERATION_TIMEOUT_MILLIS = 10L

class MockDeviceFileSystemService(val project: Project, edtExecutor: Executor, taskExecutor: Executor)
  : DeviceFileSystemService<DeviceFileSystem> {

  val edtExecutor = FutureCallbackExecutor(edtExecutor)

  override val devices = MutableStateFlow(emptyList<DeviceFileSystem>())

  fun addDevice(deviceName: String): MockDeviceFileSystem {
    val device = MockDeviceFileSystem(this, deviceName)
    devices.value += device
    return device
  }

  fun removeDevice(device: MockDeviceFileSystem): Boolean {
    val removed = devices.value.contains(device)
    devices.value -= device
    return removed
  }
}
