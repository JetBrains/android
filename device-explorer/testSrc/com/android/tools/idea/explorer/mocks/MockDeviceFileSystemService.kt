/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.mocks

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener
import com.intellij.openapi.project.Project
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor
import java.util.function.Supplier

const val OPERATION_TIMEOUT_MILLIS = 10L

class MockDeviceFileSystemService(val project: Project, edtExecutor: Executor, taskExecutor: Executor)
  : DeviceFileSystemService<DeviceFileSystem> {

  val edtExecutor = FutureCallbackExecutor(edtExecutor)
  private val myListeners: MutableList<DeviceFileSystemServiceListener> = ArrayList()
  private val myDevices: MutableList<MockDeviceFileSystem> = ArrayList()

  override fun addListener(listener: DeviceFileSystemServiceListener) {
    myListeners.add(listener)
  }

  override fun removeListener(listener: DeviceFileSystemServiceListener) {
    myListeners.remove(listener)
  }

  val listeners: Array<DeviceFileSystemServiceListener>
    get() = myListeners.toTypedArray()

  override suspend fun start(adbSupplier: Supplier<File?>) {
    delay(OPERATION_TIMEOUT_MILLIS)
  }

  override suspend fun restart(adbSupplier: Supplier<File?>) {
    coroutineScope {
      delay(OPERATION_TIMEOUT_MILLIS)
      launch(uiThread) {
        myListeners.forEach { it.serviceRestarted() }
      }
    }
  }

  override val devices: List<DeviceFileSystem>
    get() = ArrayList<DeviceFileSystem>(myDevices)

  fun addDevice(deviceName: String): MockDeviceFileSystem {
    val device = MockDeviceFileSystem(this, deviceName)
    myDevices.add(device)
    myListeners.forEach { it.deviceAdded(device) }
    return device
  }

  fun removeDevice(device: MockDeviceFileSystem): Boolean {
    val removed = myDevices.remove(device)
    if (removed) {
      myListeners.forEach { it.deviceRemoved(device) }
    }
    return removed
  }
}
