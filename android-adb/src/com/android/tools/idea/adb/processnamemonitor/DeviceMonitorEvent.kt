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
package com.android.tools.idea.adb.processnamemonitor

import com.android.ddmlib.IDevice

/**
 * Device tracking events
 */
internal sealed class DeviceMonitorEvent {
  /**
   * Sent when a device is [com.android.ddmlib.IDevice.DeviceState.ONLINE] and ready to accept ADB request
   */
  data class Online(val device: IDevice) : DeviceMonitorEvent()

  /**
   * Sent when a device is disconnected. Note that there is no guarantee this is invoked in all cases. Also note this can be invoked even
   * if a [Online] was never sent.
   */
  data class Disconnected(val device: IDevice) : DeviceMonitorEvent()
}