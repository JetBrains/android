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
package com.android.tools.idea.adb

import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.intellij.util.containers.ContainerUtil

/**
 * Todo
 */
internal class FakeAdbAdapter : AdbAdapter {
  var devices = listOf<IDevice>()

  val deviceChangeListeners = ContainerUtil.createConcurrentList<IDeviceChangeListener>()
  val clientChangeListeners = ContainerUtil.createConcurrentList<IClientChangeListener>()

  fun fireDeviceConnected(device: IDevice) {
    deviceChangeListeners.forEach { it.deviceConnected(device) }
  }

  fun fireDeviceDisconnected(device: IDevice) {
    deviceChangeListeners.forEach { it.deviceDisconnected(device) }
  }

  fun fireDeviceChange(device: IDevice, changeMask: Int) {
    deviceChangeListeners.forEach { it.deviceChanged(device, changeMask) }
  }

  fun fireClientChange(client: Client, changeMask: Int) {
    clientChangeListeners.forEach {
      it.clientChanged(client, changeMask)
    }
  }

  override suspend fun getDevices(): List<IDevice> = devices

  override fun addDeviceChangeListener(listener: IDeviceChangeListener) {
    deviceChangeListeners.add(listener)
  }

  override fun removeDeviceChangeListener(listener: IDeviceChangeListener) {
    deviceChangeListeners.remove(listener)
  }

  override fun addClientChangeListener(listener: IClientChangeListener) {
    clientChangeListeners.add(listener)
  }

  override fun removeClientChangeListener(listener: IClientChangeListener) {
    clientChangeListeners.remove(listener)
  }
}