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
package com.android.tools.idea.transport

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.manager.StreamQueryUtils
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class StreamQueryUtilsTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("StreamQuerySupportTest", transportService)


  @Test
  fun queryForDevices() {
    val transportClient = TransportClient(grpcServerRule.name)

    transportService.addDevice(createDevice(AndroidVersion.VersionCodes.O, "O_DEVICE", Common.Device.State.ONLINE))
    transportService.addDevice(createDevice(AndroidVersion.VersionCodes.S, "S_DEVICE", Common.Device.State.ONLINE))

    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    transportService.addDevice(createDevice(AndroidVersion.VersionCodes.O, "O_DEVICE", Common.Device.State.DISCONNECTED))

    val devices = StreamQueryUtils.queryForDevices(transportClient.transportStub)
    assertThat(devices).hasSize(2)
    assertThat(devices.map { it.device }).containsExactly(
      createDevice(AndroidVersion.VersionCodes.S, "S_DEVICE", Common.Device.State.ONLINE),
      createDevice(AndroidVersion.VersionCodes.O, "O_DEVICE", Common.Device.State.DISCONNECTED)
    )
  }

  @Test
  fun queryForProcesses() {
    val transportClient = TransportClient(grpcServerRule.name)

    val device = createDevice(AndroidVersion.VersionCodes.S, "S_DEVICE", Common.Device.State.ONLINE)
    transportService.addDevice(device)

    transportService.addProcess(device, createProcess(device.deviceId, 20, "FakeProcess1", Common.Process.State.ALIVE))
    transportService.addProcess(device, createProcess(device.deviceId, 33, "FakeProcess2", Common.Process.State.ALIVE))

    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    transportService.addProcess(device, createProcess(device.deviceId, 33, "FakeProcess2", Common.Process.State.DEAD))

    // Verify with a filter that always returns true
    assertThat(StreamQueryUtils.queryForProcesses(transportClient.transportStub, device.deviceId) { _, _ -> true })
      .containsExactly(
        createProcess(device.deviceId, 20, "FakeProcess1", Common.Process.State.ALIVE),
        createProcess(device.deviceId, 33, "FakeProcess2", Common.Process.State.DEAD)
      )

    // Verify with filter
    assertThat(
      StreamQueryUtils.queryForProcesses(transportClient.transportStub,
                                         device.deviceId) { _, lastAliveEvent -> lastAliveEvent.pid == 20 })
      .containsExactly(createProcess(device.deviceId, 20, "FakeProcess1", Common.Process.State.ALIVE))
  }

  @Test
  fun processesQueryReturnsHighestExposureLevel() {
    val transportClient = TransportClient(grpcServerRule.name)

    val device = createDevice(AndroidVersion.VersionCodes.S, "S_DEVICE", Common.Device.State.ONLINE)
    transportService.addDevice(device)
    transportService.addProcess(device, createProcess(device.deviceId, 20, "FakeProcess1", Common.Process.State.ALIVE))
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Insert an event with a lower exposure level for the same process, and verify the highest exposure event is returned.
    transportService.addProcess(device, createProcess(device.deviceId, 20, "FakeProcess1", Common.Process.State.ALIVE,
                                                      Common.Process.ExposureLevel.PROFILEABLE))

    assertThat(StreamQueryUtils.queryForProcesses(transportClient.transportStub, device.deviceId) { _, _ -> true })
      .containsExactly(
        createProcess(device.deviceId, 20, "FakeProcess1", Common.Process.State.ALIVE, Common.Process.ExposureLevel.DEBUGGABLE))
  }

  private fun createDevice(
    featureLevel: Int,
    serial: String,
    state: Common.Device.State
  ): Common.Device {
    return Common.Device.newBuilder()
      .setDeviceId(serial.hashCode().toLong())
      .setFeatureLevel(featureLevel)
      .setSerial(serial)
      .setState(state)
      .build()
  }

  private fun createProcess(
    deviceId: Long,
    pid: Int,
    name: String,
    state: Common.Process.State,
    exposureLevel: Common.Process.ExposureLevel = Common.Process.ExposureLevel.DEBUGGABLE
  ): Common.Process {
    return Common.Process.newBuilder()
      .setDeviceId(deviceId)
      .setPid(pid)
      .setName(name)
      .setState(state)
      .setExposureLevel(exposureLevel)
      .build()
  }
}