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

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.delayedVoid
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.Executor
import java.util.function.Supplier

const val OPERATION_TIMEOUT_MILLIS = 10

class MockDeviceFileSystemService(val project: Project, edtExecutor: Executor, taskExecutor: Executor)
  : DeviceFileSystemService<DeviceFileSystem> {

  val edtExecutor = FutureCallbackExecutor(edtExecutor)
  private val myTaskExecutor = FutureCallbackExecutor(taskExecutor)
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

  override fun start(adbSupplier: Supplier<File?>): ListenableFuture<Void> {
    return delayedVoid(OPERATION_TIMEOUT_MILLIS)
  }

  override fun restart(adbSupplier: Supplier<File?>): ListenableFuture<Void> {
    val futureResult = delayedVoid(OPERATION_TIMEOUT_MILLIS)
    edtExecutor.addCallback(futureResult, object : FutureCallback<Void> {
      override fun onSuccess(result: Void?) {
        myListeners.forEach  { it.serviceRestarted() }
      }

      override fun onFailure(t: Throwable) {}
    })
    return futureResult
  }

  override val devices: ListenableFuture<List<DeviceFileSystem>>
    get() = Futures.immediateFuture(ArrayList<DeviceFileSystem>(myDevices))

  fun addDevice(deviceName: String): MockDeviceFileSystem {
    val device = MockDeviceFileSystem(this, deviceName, myTaskExecutor)
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
