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
package com.android.tools.idea.device.explorer

import com.android.adblib.ConnectedDevice
import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.isOnline
import com.android.sdklib.deviceprovisioner.pairWithNestedState
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@UiThread
class DeviceExplorerModel @NonInjectable constructor(private val deviceProvisioner: DeviceProvisioner) {
  constructor(project: Project) : this(project.service<DeviceProvisionerService>().deviceProvisioner)

  private val coroutineScope = deviceProvisioner.scope.createChildScope(true)

  val devices: StateFlow<List<DeviceHandle>> =
    flow {
      val deviceMap = mutableMapOf<DeviceHandle, ConnectedDevice>()

      deviceProvisioner.devices
        .pairWithNestedState { deviceHandle -> deviceHandle.stateFlow.map { deviceState -> deviceState.connectedDevice } }
        .collect { pairs: List<Pair<DeviceHandle, ConnectedDevice?>> ->
          // Remove any device that is no longer present
          val handles = pairs.map { it.first }.toSet()
          deviceMap.keys.retainAll(handles)

          // Add new devices and update existing devices
          for ((handle, connectedDevice) in pairs) {
            if (handle.state.isOnline()) {
              deviceMap.compute(handle) { _, _ -> connectedDevice }
            } else  {
              // Remove offline devices if they've been added
              deviceMap.remove(handle)
            }
          }
          emit(deviceMap.keys.toList())
        }
    }
      .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

  val activeDevice = MutableStateFlow<DeviceHandle?>(null)

  fun setActiveDevice(device: DeviceHandle?) {
    if (device != activeDevice.value) {
      activeDevice.value = device
    }
  }
}