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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.adblib.ConnectedDevice
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.pairWithNestedState
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystemService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.ide.PooledThreadExecutor

/**
 * Abstraction over ADB devices and their file system.
 *
 * While typically obtained as a project service, its actual dependency is on the DeviceProvisioner
 * and its lifetime is tied to that.
 */
class AdbDeviceFileSystemService
@NonInjectable
constructor(
  private val deviceProvisioner: DeviceProvisioner
) : DeviceFileSystemService<AdbDeviceFileSystem> {

  constructor(
    project: Project
  ) : this(
    project.service<DeviceProvisionerService>().deviceProvisioner
  )

  companion object {
    @JvmStatic fun getInstance(project: Project): AdbDeviceFileSystemService = project.service()
  }

  private val coroutineScope = deviceProvisioner.scope.createChildScope(true)

  private val edtExecutor = FutureCallbackExecutor(EdtExecutorService.getInstance())
  private val dispatcher = PooledThreadExecutor.INSTANCE.asCoroutineDispatcher()

  /**
   * Provides the current set of [DeviceFileSystem] instances. These each correspond to a single
   * [ConnectedDevice] instance. If the device disconnects and reconnects, we will have a new
   * [DeviceFileSystem] instance, even if the [DeviceHandle] remains the same.
   *
   * Note, however, that changes of state within the [DeviceHandle] or [ConnectedDevice] do not
   * cause this flow to update.
   */
  override val devices: StateFlow<List<DeviceFileSystem>> =
    flow {
        val fileSystems = mutableMapOf<DeviceHandle, AdbDeviceFileSystem>()

        deviceProvisioner.devices
          .pairWithNestedState { it.stateFlow.map { it.connectedDevice } }
          .collect { pairs: List<Pair<DeviceHandle, ConnectedDevice?>> ->
            // Remove any device that is no longer present
            val handles = pairs.map { it.first }.toSet()
            fileSystems.keys.retainAll(handles)

            // Add new devices and update existing devices
            for ((handle, connectedDevice) in pairs) {
              fileSystems.compute(handle) { _, fileSystem ->
                when (connectedDevice) {
                  null -> null
                  fileSystem?.device -> fileSystem
                  else -> newDeviceFileSystem(handle, connectedDevice)
                }
              }
            }
            emit(fileSystems.values.toList())
          }
      }
      .stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

  private fun newDeviceFileSystem(handle: DeviceHandle, connectedDevice: ConnectedDevice) =
    AdbDeviceFileSystem(handle, connectedDevice, edtExecutor, dispatcher)
}
