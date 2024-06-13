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
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection

object TaskModelTestUtils {
  fun addDeviceWithProcess(device: Common.Device, process: Common.Process, transportService: FakeTransportService, timer: FakeTimer) {
    transportService.addDevice(device)
    transportService.addProcess(device, process)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  fun createDevice(deviceName: String, deviceState: Common.Device.State): Common.Device = createDevice(deviceName, deviceState, deviceName)

  fun createDevice(deviceName: String,
                   deviceState: Common.Device.State,
                   serial: String): Common.Device = Common.Device.newBuilder().setDeviceId(deviceName.hashCode().toLong()).setSerial(
    serial).setState(deviceState).build()

  fun createDevice(deviceName: String, deviceState: Common.Device.State, version: String, apiLevel: Int): Common.Device = createDevice(
    deviceName, deviceName, deviceName.hashCode().toLong(), deviceState, version, apiLevel)

  fun createDevice(deviceName: String,
                   serial: String,
                   deviceId: Long,
                   deviceState: Common.Device.State,
                   version: String,
                   apiLevel: Int): Common.Device = Common.Device.newBuilder().setDeviceId(deviceId).setSerial(serial).setState(
    deviceState).setModel(deviceName).setVersion(version).setApiLevel(apiLevel).setFeatureLevel(apiLevel).build()

  fun createProcess(pid: Int,
                    processName: String,
                    processState: Common.Process.State,
                    deviceId: Long): Common.Process = createProcess(pid, processName, processState, deviceId,
                                                                    Common.Process.ExposureLevel.DEBUGGABLE)

  fun createProcess(pid: Int,
                    processName: String,
                    processState: Common.Process.State,
                    deviceId: Long,
                    exposureLevel: Common.Process.ExposureLevel): Common.Process =
    Common.Process.newBuilder().setDeviceId(deviceId).setPid(pid).setName(processName).setState(processState).setExposureLevel(
      exposureLevel).build()

  fun createProfilerDeviceSelection(featureLevel: Int, isRunning: Boolean) = ProfilerDeviceSelection("FakeDevice", featureLevel, isRunning,
                                                                                                     Common.Device.newBuilder().setModel(
                                                                                                       "FakeDevice").setFeatureLevel(
                                                                                                       featureLevel).build())

  fun updateDeviceState(deviceName: String, deviceState: Common.Device.State, transportService: FakeTransportService, timer: FakeTimer) {
    val newDevice = createDevice(deviceName, deviceState)
    transportService.addDevice(newDevice)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }
}