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
import com.android.tools.idea.layoutinspector.util.ProcessManagerSync
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val DEVICE1 = "1234"

private const val PROCESS1 = 12345
private const val PROCESS2 = 12346
private const val PROCESS3 = 12347

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
    AndroidDebugBridge.initIfNeeded(true)
    bridge = AndroidDebugBridge.createBridge()
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
  fun testAddProcess() {
    startProcesses()
  }

  @Test
  fun testRemoveProcess() {
    val manager = startProcesses()

    val device1 = adbServer!!.deviceListCopy.get().single()
    device1.stopClient(PROCESS1)

    val waiter = ProcessManagerSync(manager)
    waiter.waitUntilReady(DEVICE1, PROCESS2)
  }

  @Test
  fun testStopDevice() {
    val manager = startProcesses()

    adbServer!!.disconnectDevice(DEVICE1)

    val waiter = ProcessManagerSync(manager)
    waiter.waitUntilReady(DEVICE1)
  }

  /**
   * Starts one [DEVICE1] with 3 processes: [PROCESS1], [PROCESS2], [PROCESS3]
   *
   * Only the first 2 processes are available for use with the Layout Inspector: [PROCESS1] & [PROCESS2].
   * [PROCESS3] does not have a view hierarchy feature and is therefore ignored.
   */
  private fun startProcesses(): LegacyProcessManager {
    val manager = LegacyProcessManager(disposableRule.disposable)
    assertThat(manager.getStreams().toList()).isEmpty()
    val device1 = adbServer!!.connectDevice(DEVICE1, MANUFACTURER, "My Model", "3.0", "27", DeviceState.HostConnectionType.USB)?.get()!!
    device1.deviceStatus = DeviceState.DeviceStatus.ONLINE

    device1.startClient(PROCESS1, 123, "com.example.myapplication", true)
    device1.startClient(PROCESS2, 234, "com.example.basic_app", true)
    device1.startClient(PROCESS3, 345, "com.example.compose_app", true)

    val waiter = ProcessManagerSync(manager)
    waiter.waitUntilReady(DEVICE1, PROCESS1, PROCESS2)
    return manager
  }
}
