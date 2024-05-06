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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common

object TaskModelTestUtils {
  fun addDeviceWithProcess(device: Common.Device, process: Common.Process, transportService: FakeTransportService, timer: FakeTimer) {
    transportService.addDevice(device)
    transportService.addProcess(device, process)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  fun createDevice(deviceName: String, deviceState: Common.Device.State) = Common.Device.newBuilder().setDeviceId(
    deviceName.hashCode().toLong()).setSerial(deviceName).setState(deviceState).build()

  fun createProcess(pid: Int,
                            processName: String,
                            processState: Common.Process.State,
                            deviceId: Long) = Common.Process.newBuilder().setDeviceId(deviceId).setPid(pid).setName(processName).setState(
    processState).setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE).build()
}