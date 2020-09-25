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
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.FeaturesHandler
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.util.ProcessManagerAsserts
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val DEVICE1 = "1234"
private const val EMULATOR1 = "emulator-678"
private const val EMULATOR2 = "emulator-789"

private const val PROCESS1 = 12345
private const val PROCESS2 = 12346
private const val PROCESS3 = 12347
private const val PROCESS4 = 12348
private const val PROCESS5 = 12349

private const val MANUFACTURER = "Google"

private const val FIND_DEVICE_RETRY_COUNT = 10

class LegacyProcessManagerTest {

  @get:Rule
  val disposableRule = DisposableRule()

  private var adbServer: FakeAdbServer? = null
  private var bridge: AndroidDebugBridge? = null
  private val emulatorRegEx = IDevice.RE_EMULATOR_SN.toRegex()

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
    bridge!!.devices.forEach { adbServer!!.disconnectDevice(it.serialNumber).get() }
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
    adbServer?.close()
    adbServer = null
    bridge = null
  }

  @Test
  fun addDevice() {
    startDevice1()
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

    adbServer!!.disconnectDevice(DEVICE1).get()

    val waiter = ProcessManagerAsserts(manager)
    waiter.assertNoDevices()
  }

  @Test
  fun testDeviceNotAdded() {
    val scheduler = VirtualTimeScheduler()
    val manager = LegacyProcessManager(disposableRule.disposable, scheduler)
    assertThat(manager.getStreams().toList()).isEmpty()

    val device1 = adbServer!!.connectDevice(DEVICE1, MANUFACTURER, "My Model", "3.0", "27", DeviceState.HostConnectionType.USB)?.get()!!
    device1.deviceStatus = DeviceState.DeviceStatus.OFFLINE
    // Device is offline, so we won't be able to add it. Exhaust the retries.
    repeat(200) { scheduler.advanceBy(50, TimeUnit.MILLISECONDS) }
    val waiter = ProcessManagerAsserts(manager)
    waiter.assertNoDevices()
    // Now it comes online
    device1.deviceStatus = DeviceState.DeviceStatus.ONLINE
    // Advance time, but we should have already given up
    scheduler.advanceBy(50, TimeUnit.MILLISECONDS)
    waiter.assertNoDevices()

    device1.startClient(PROCESS1, 123, "com.example.myapplication", true)
    // Now we should be able to see the device
    waiter.assertDeviceWithProcesses(DEVICE1, PROCESS1) { scheduler.advanceBy(50, TimeUnit.MILLISECONDS) }
  }

  @Test
  fun testEmulator() {
    val (_, manager) = startDevice(EMULATOR1, MANUFACTURER, "My Avd", "2.0", "28")
    val stream = manager.getStreams().firstOrNull() ?: error("Emulator not found")
    val preferred = LayoutInspectorPreferredProcess(findDevice(EMULATOR1)!!, "com.example")
    assertThat(stream.device.manufacturer).isEqualTo(MANUFACTURER)
    assertThat(stream.device.model).isEqualTo("My Avd")
    assertThat(preferred.isDeviceMatch(stream.device)).isTrue()
  }

  @Test
  fun testEmulatorWithoutUnknownManufacturer() {
    val (_, manager) = startDevice(EMULATOR2, "unknown", "My Pixel", "2.0", "28")
    val stream = manager.getStreams().firstOrNull() ?: error("Emulator not found")
    val preferred = LayoutInspectorPreferredProcess(findDevice(EMULATOR2)!!, "com.example")
    assertThat(stream.device.manufacturer).isEqualTo("Emulator")
    assertThat(stream.device.model).isEqualTo("My Pixel")
    assertThat(preferred.isDeviceMatch(stream.device)).isTrue()
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
    val (device1, manager) = startDevice1()
    device1.startClient(PROCESS1, 123, "com.example.myapplication", true)
    device1.startClient(PROCESS2, 234, "com.example.basic_app", true)
    device1.startClient(PROCESS3, 345, "com.example.compose_app", true)
    device1.startClient(PROCESS4, 456, "", true)
    device1.startClient(PROCESS5, 567, ClientData.PRE_INITIALIZED, true)

    val waiter = ProcessManagerAsserts(manager)
    waiter.assertDeviceWithProcesses(DEVICE1, PROCESS1, PROCESS2)
    return manager
  }

  private fun startDevice1(): Pair<DeviceState, LegacyProcessManager> =
    startDevice(DEVICE1, MANUFACTURER, "My Model", "3.0", "27")

  private fun startDevice(
    serial: String,
    manufacturer: String,
    model: String,
    release: String,
    sdk: String
  ): Pair<DeviceState, LegacyProcessManager> {
    val scheduler = VirtualTimeScheduler()
    val manager = LegacyProcessManager(disposableRule.disposable, scheduler)
    assertThat(manager.getStreams().toList()).isEmpty()

    val device1 = adbServer!!.connectDevice(serial, manufacturer, model, release, sdk, DeviceState.HostConnectionType.USB)?.get()!!
    device1.deviceStatus = DeviceState.DeviceStatus.ONLINE
    assertThat(manager.getStreams().toList()).isEmpty()

    if (emulatorRegEx.matches(serial)) {
      setAvdNameByReflection(serial, model)
    }

    // The LegacyProcessManager will not register the device before the properties are loaded by the thread
    // created in: PropertyFetcher.initiatePropertiesQuery.
    // Wait for that here while advancing the time scheduler to force another check of the properties being loaded.
    val waiter = ProcessManagerAsserts(manager)
    waiter.assertDeviceWithProcesses(serial) { scheduler.advanceBy(50, TimeUnit.MILLISECONDS) }

    return Pair(device1, manager)
  }

  private fun setAvdNameByReflection(serial: String, avdName: String) {
    val device = findDevice(serial) as? DeviceImpl ?: error("Emulator not found: $serial")
    val field = device.javaClass.getDeclaredField("mAvdName") ?: error("Could not set AvdName on emulator device")
    field.isAccessible = true
    field.set(device, avdName)
  }

  private fun findDevice(serial: String): IDevice? {
    var retries = 0
    while (retries++ < FIND_DEVICE_RETRY_COUNT) {
      Thread.sleep(100)
      bridge?.devices?.singleOrNull { it.serialNumber == serial }?.let { return it }
    }
    return null
  }
}
