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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice

class FakeDeviceConnection: DeviceConnection {
  private val clientChangeListeners = mutableListOf<AndroidDebugBridge.IClientChangeListener>()
  private val deviceChangeListeners = mutableListOf<AndroidDebugBridge.IDeviceChangeListener>()
  override fun addClientChangeListener(listener: AndroidDebugBridge.IClientChangeListener) {
    clientChangeListeners.add(listener)
  }

  override fun addDeviceChangeListener(listener: AndroidDebugBridge.IDeviceChangeListener) {
    deviceChangeListeners.add(listener)
  }

  fun clientChanged(client: Client, changeMask: Int) {
    clientChangeListeners.forEach { it.clientChanged(client, changeMask) }
  }

  fun deviceChanged(device: IDevice, changeMask: Int) {
    deviceChangeListeners.forEach { it.deviceChanged(device, changeMask) }
  }
}