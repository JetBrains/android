/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.ClientData
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.FeaturesHandler
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.layoutinspector.util.ProcessManagerAsserts
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val DEVICE1 = "1234"

private const val PROCESS1 = 12345
private const val PROCESS2 = 12346
private const val PROCESS3 = 12347
private const val PROCESS4 = 12348
private const val PROCESS5 = 12349

private const val MANUFACTURER = "Google"

class LegacyProcessManagerTest {

  @get:Rule
  val disposableRule = DisposableRule()

  private var adbServer: FakeAdbServer? = null
  private var bridge: AndroidDebugBridge? = null

  @Before
  fun before() {
    adbServer = FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .addDeviceHandler(JdwpCommandHandler().addPacketHandler(FeaturesHandler.CHUNK_TYPE, FeaturesHandler(
        mapOf(PROCESS1 to listOf(ClientData.FEATURE_VIEW_HIERARCHY),
              PROCESS2 to listOf(ClientData.FEATURE_VIEW_HIERARCHY)),
        listOf(ClientData.FEATURE_OPENGL_TRACING))))
      .build()
    adbServer?.start()
    AndroidDebugBridge.enableFakeAdbServerMode(adbServer!!.port)
    AndroidDebugBridge.init(true)
    bridge = AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS) ?: error("Could not create ADB bridge")
  }

  @After
  fun after() {
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
    adbServer?.close()
    adbServer = null
    bridge = null
  }

  @Test
  fun addDevice() {
    startDevice()
  }

  @Test
  fun testAddProcess() {
    startProcesses()
  }

  @Test
  fun testRemoveProcess() {
    val manager = startProcesses()

    val device1 = adbServer!!.deviceListCopy.get().single()
    device1.stopClient(PROCESS1)

    val waiter = ProcessManagerAsserts(manager)
    waiter.assertDeviceWithProcesses(DEVICE1, PROCESS2)
  }

  @Test
  fun testStopDevice() {
    val manager = startProcesses()

    adbServer!!.disconnectDevice(DEVICE1)

    val waiter = ProcessManagerAsserts(manager)
    waiter.assertNoDevices()
  }

  /**
   * Starts one [DEVICE1] with 5 processes: [PROCESS1], [PROCESS2], [PROCESS3], [PROCESS4], [PROCESS5]
   *
   * Only the first 2 processes are available for use with the Layout Inspector: [PROCESS1] & [PROCESS2].
   * [PROCESS3] does not have a view hierarchy feature and is ignored.
   * [PROCESS4] has an empty package name and ignored.
   * [PROCESS5] has a package name of "<pre-initialized>" indicating it is not fully initialized and is ignored.
   */
  private fun startProcesses(): LegacyProcessManager {
    val (device1, manager) = startDevice()
    device1.startClient(PROCESS1, 123, "com.example.myapplication", true)
    device1.startClient(PROCESS2, 234, "com.example.basic_app", true)
    device1.startClient(PROCESS3, 345, "com.example.compose_app", true)
    device1.startClient(PROCESS4, 456, "", true)
    device1.startClient(PROCESS5, 567, ClientData.PRE_INITIALIZED, true)

    val waiter = ProcessManagerAsserts(manager)
    waiter.assertDeviceWithProcesses(DEVICE1, PROCESS1, PROCESS2)
    return manager
  }

  private fun startDevice(): Pair<DeviceState, LegacyProcessManager> {
    val scheduler = VirtualTimeScheduler()
    val manager = LegacyProcessManager(disposableRule.disposable, scheduler)
    assertThat(manager.getStreams().toList()).isEmpty()

    val device1 = adbServer!!.connectDevice(DEVICE1, MANUFACTURER, "My Model", "3.0", "27", DeviceState.HostConnectionType.USB)?.get()!!
    device1.deviceStatus = DeviceState.DeviceStatus.ONLINE
    assertThat(manager.getStreams().toList()).isEmpty()

    // The LegacyProcessManager will not register the device before the properties are loaded by the thread
    // created in: PropertyFetcher.initiatePropertiesQuery.
    // Wait for that here while advancing the time scheduler to force another check of the properties being loaded.
    val waiter = ProcessManagerAsserts(manager)
    waiter.assertDeviceWithProcesses(DEVICE1) { scheduler.advanceBy(50, TimeUnit.MILLISECONDS) }

    return Pair(device1, manager)
  }
}
