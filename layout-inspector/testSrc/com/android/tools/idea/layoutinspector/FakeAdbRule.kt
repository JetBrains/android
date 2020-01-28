/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Uninterruptibles
import org.junit.rules.ExternalResource
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Rule that sets up and tears down a FakeAdbServer, and provides some convenience methods for interacting with it.
 *
 * TODO: move this to a common place
 */
class FakeAdbRule : ExternalResource() {
  var fakeAdbServer: FakeAdbServer? = null

  private val startingDevices: MutableMap<String, CountDownLatch> = mutableMapOf()
  private val hostCommandHandlers: MutableMap<String, () -> HostCommandHandler> = mutableMapOf()
  private val deviceCommandHandlers: MutableList<DeviceCommandHandler> = mutableListOf(
    object : DeviceCommandHandler("track-jdwp") {
      override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
        startingDevices[device.deviceId]?.countDown()
        return false
      }
    }
  )

  /**
   * Add a [HostCommandHandler]. Must be called before @Before tasks are run.
   */
  fun withHostCommandHandler(command: String, handlerConstructor: () -> HostCommandHandler) = apply {
    hostCommandHandlers[command] = handlerConstructor
  }

  /**
   * Add a [DeviceCommandHandler]. Must be called before @Before tasks are run.
   */
  fun withDeviceCommandHandler(handler: DeviceCommandHandler) = apply {
    deviceCommandHandlers.add(handler)
  }

  fun attachDevice(deviceId: String,
                   manufacturer: String,
                   model: String,
                   release: String,
                   sdk: String,
                   hostConnectionType: DeviceState.HostConnectionType) {
    val startLatch = CountDownLatch(1)
    startingDevices[deviceId] = startLatch
    val device = fakeAdbServer?.connectDevice(deviceId, manufacturer, model, release, sdk, hostConnectionType)?.get()!!
    device.deviceStatus = DeviceState.DeviceStatus.ONLINE
    assertThat(startLatch.await(30, TimeUnit.SECONDS)).isTrue()
  }


  override fun before() {
    val builder = FakeAdbServer.Builder().installDefaultCommandHandlers()
    hostCommandHandlers.forEach { (command, constructor) -> builder.setHostCommandHandler(command, constructor) }
    deviceCommandHandlers.forEach { builder.addDeviceHandler(it) }
    fakeAdbServer = builder.build()
    fakeAdbServer?.start()

    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer!!.port)
    AndroidDebugBridge.initIfNeeded(true)

    val bridge = AndroidDebugBridge.createBridge()
    val startTime = System.currentTimeMillis()
    while (!bridge.isConnected && System.currentTimeMillis() - startTime < TimeUnit.SECONDS.toMillis(10)) {
      Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS)
    }
  }

  override fun after() {
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
    fakeAdbServer?.close()
  }
}