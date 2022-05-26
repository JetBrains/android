/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.tools.idea.ddms.DeviceContext.DeviceSelectionListener
import com.intellij.openapi.application.runInEdt

/**
 * A [DeviceSelectionListener] that only cares about device connect/disconnect events. A device connect happens when a
 * connected device is selected or a selected device is connected.
 */
abstract class DeviceConnectionListener : DeviceSelectionListener {
  private var device: IDevice? = null
  private var deviceState: IDevice.DeviceState? = null

  override fun deviceSelected(device: IDevice?) {
    val currentDevice = this.device
    if (device == currentDevice) {
      return
    }
    if (device == null && currentDevice != null) {
      this.device = null
      deviceState = null
      runInEdt { onDeviceDisconnected(currentDevice) }

    }
    else {
      this.device = device
      deviceState = device?.state
      if (device?.state == ONLINE) {
        runInEdt { onDeviceConnected(device) }
      }
    }
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    if (this.device != device || changeMask != CHANGE_STATE || deviceState == device.state) {
      return
    }
    deviceState = device.state
    if (deviceState == DISCONNECTED || deviceState == OFFLINE) {
      runInEdt { onDeviceDisconnected(device) }
    }
    else if (deviceState == ONLINE) {
      runInEdt {
        onDeviceConnected(device)
      }
    }
  }

  override fun clientSelected(c: Client?) {}

  @UiThread
  abstract fun onDeviceConnected(device: IDevice)

  @UiThread
  abstract fun onDeviceDisconnected(device: IDevice)
}