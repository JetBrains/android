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
package com.android.tools.idea.logcat.devices

import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbSession
import com.android.adblib.DeviceState.OFFLINE
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.logcat.devices.DeviceEvent.TrackingReset
import com.android.tools.idea.logcat.testing.TestDevice
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.Socket
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


private const val GET_PROPS_DEVICE =
  "getprop ro.build.version.release ; getprop ro.build.version.sdk ; getprop ro.product.manufacturer ; getprop ro.product.model"

private const val GET_PROPS_EMULATOR =
  "getprop ro.build.version.release ; getprop ro.build.version.sdk ; getprop ro.boot.qemu.avd_name ; getprop ro.kernel.qemu.avd_name"

/**
 * Tests for [DeviceComboBoxDeviceTracker]
 */
@Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
class DeviceComboBoxDeviceTrackerTest {
  private val projectRule = ProjectRule()
  private val fakeAdb = FakeAdbRule()
  private val closeables = CloseablesRule()

  private val deviceCommandHandler = MyDeviceCommandHandler()

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdb.withDeviceCommandHandler(deviceCommandHandler), closeables)

  private val device1 = TestDevice("device-1", ONLINE, 11, 30, "manufacturer1", "model1")
  private val device2 = TestDevice("device-2", ONLINE, 12, 31, "manufacturer2", "model2")
  private val emulator1 = TestDevice("emulator-1", ONLINE, 11, 30, avdName = "avd1")
  private val emulator2 = TestDevice("emulator-2", ONLINE, 11, 30, avdNamePre31 = "avd2")
  private val emulator3 = TestDevice("emulator-3", ONLINE, 11, 30, avdName = "", avdNamePre31 = "")

  private val events = mutableListOf<DeviceEvent>()

  @Before
  fun setUp() {
    deviceCommandHandler.setupDevice(device1)
    deviceCommandHandler.setupDevice(device2)
    deviceCommandHandler.setupDevice(emulator1)
    deviceCommandHandler.setupDevice(emulator2)
    deviceCommandHandler.setupDevice(emulator3)
  }

  @Test
  fun name(): Unit = runBlocking {
    fakeAdb.attachDevices(device1)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())

    launch {
      deviceTracker.trackDevices(retryOnException = false).toList(events)
    }

    yieldUntil { events.size == 1 }
    //waitForCondition(5, SECONDS) { events.size == 1 }
    println(events)
    fakeAdb.stop()
  }

  @Test
  fun initialDevices(): Unit = runBlocking {
    val initialDevices = arrayOf(device1, emulator1, emulator2, emulator3)
    fakeAdb.attachDevices(*initialDevices)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())

    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }

    yieldUntil { events.size == initialDevices.size }
    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(device1.device),
      Added(emulator1.device),
      Added(emulator2.device),
      Added(emulator3.device.copy(deviceId = emulator3.serialNumber, name = emulator3.serialNumber)),
    )
  }

  @Test
  fun initialDevices_ignoresOffline(): Unit = runBlocking {
    fakeAdb.attachDevices(device1, device2.withState(OFFLINE))
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())

    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }

    yieldUntil { events.size == 1 }
    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(device1.device),
    )
  }

  @Test
  fun initialDevices_withInitialPreexistingDevice(): Unit = runBlocking {
    val preexistingEmulator = emulator1.withState(OFFLINE).withSerialNumber("")
    fakeAdb.attachDevices(device1)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession(), preexistingDevice = preexistingEmulator.device)

    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }

    yieldUntil { events.size == 2 }
    fakeAdb.stop()
    job.join()

    assertThat(events.dropTrackingReset()).containsExactly(
      Added(device1.device),
      Added(preexistingEmulator.device),
    )
  }

  @Test
  fun initialDevices_withInitialPreexistingDeviceMatchingOnlineDevice(): Unit = runBlocking {
    val preexistingEmulator = emulator1.withState(OFFLINE).withSerialNumber("")
    fakeAdb.attachDevices(emulator1, device1)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession(), preexistingDevice = preexistingEmulator.device)

    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }

    yieldUntil { events.size == 2 }
    fakeAdb.stop()
    job.join()

    assertThat(events.dropTrackingReset()).containsExactly(
      Added(emulator1.device),
      Added(device1.device),
    )
  }

  @Test
  fun deviceAdded(): Unit = runBlocking {
    val preexistingDevice = device1.withState(OFFLINE).device
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession(), preexistingDevice = preexistingDevice)
    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }
    yieldUntil { events.size == 1 }

    fakeAdb.attachDevices(device2)
    fakeAdb.attachDevices(emulator1)

    yieldUntil { events.size == 3 }
    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(preexistingDevice),
      Added(device2.device),
      Added(emulator1.device),
    )
  }

  @Test
  fun changeState_goesOffline(): Unit = runBlocking {
    val deviceState = fakeAdb.attachDevice(device1)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())
    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }
    yieldUntil { events.size == 1 }

    deviceState.deviceStatus = DeviceState.DeviceStatus.OFFLINE

    yieldUntil { events.size == 2 }

    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(device1.device),
      StateChanged(device1.withState(OFFLINE).device),
    ).inOrder()
  }

  @Test
  fun changeState_goesOfflineComesOnline(): Unit = runBlocking {
    val deviceState = fakeAdb.attachDevice(device1)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())
    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }
    yieldUntil { events.size == 1 }

    deviceState.deviceStatus = DeviceState.DeviceStatus.OFFLINE
    yieldUntil { events.size == 2 }
    deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
    yieldUntil { events.size == 3 }

    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(device1.device),
      StateChanged(device1.withState(OFFLINE).device),
      StateChanged(device1.device),
    ).inOrder()
  }

  @Test
  fun changeState_disconnects(): Unit = runBlocking {
    fakeAdb.attachDevice(device1)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())
    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }
    yieldUntil { events.size == 1 }

    fakeAdb.disconnectDevice(device1.serialNumber)
    yieldUntil { events.size == 2 }

    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(device1.device),
      StateChanged(device1.withState(OFFLINE).device),
    ).inOrder()
  }


  @Test
  fun changeState_emulatorComesOnlineWithDifferentSerialNumber(): Unit = runBlocking {
    val emulator = emulator1.withSerialNumber("emulator-1")
    fakeAdb.attachDevice(emulator)
    val deviceTracker = deviceComboBoxDeviceTracker(adbSession = fakeAdb.createAdbSession())
    val job = launch { deviceTracker.trackDevices(retryOnException = false).toList(events) }
    yieldUntil { events.size == 1 }

    fakeAdb.disconnectDevice(emulator1.serialNumber)
    yieldUntil { events.size == 2 }
    val emulatorReconnectedOnDifferentPort = emulator1.withSerialNumber("emulator-2")
    deviceCommandHandler.setupDevice(emulatorReconnectedOnDifferentPort)
    fakeAdb.attachDevice(emulatorReconnectedOnDifferentPort)
    yieldUntil { events.size == 3 }

    fakeAdb.stop()
    job.join()
    assertThat(events.dropTrackingReset()).containsExactly(
      Added(emulator.device),
      StateChanged(emulator1.withState(OFFLINE).device),
      StateChanged(emulatorReconnectedOnDifferentPort.device),
    ).inOrder()
  }

  private fun deviceComboBoxDeviceTracker(
    preexistingDevice: Device? = null,
    adbSession: AdbSession = fakeAdb.createAdbSession(),
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) = DeviceComboBoxDeviceTracker(projectRule.project, preexistingDevice, adbSession, coroutineContext)

  private class MyDeviceCommandHandler : DeviceCommandHandler("") {

    private val devices = mutableMapOf<String, TestDevice>()

    fun setupDevice(device: TestDevice) {
      devices[device.serialNumber] = device
    }

    override fun accept(server: FakeAdbServer, socket: Socket, deviceState: DeviceState, command: String, args: String): Boolean {
      if (command != "shell") {
        return false
      }
      val result = when (args) {
        GET_PROPS_DEVICE -> getDeviceProps(deviceState.deviceId)
        GET_PROPS_EMULATOR -> getEmulatorProps(deviceState.deviceId)
        else -> return false
      }

      val stream = socket.getOutputStream()
      writeOkay(stream)
      writeString(stream, result)

      return true
    }

    private fun getDeviceProps(serialNumber: String): String {
      val device = getDevice(serialNumber)
      return """
        ${device.release}
        ${device.sdk}
        ${device.manufacturer}
        ${device.model}
      """.trimIndent() + "\n"
    }

    private fun getEmulatorProps(serialNumber: String): String {
      val device = getDevice(serialNumber)
      return """
        ${device.release}
        ${device.sdk}
        ${device.avdName}
        ${device.avdNamePre31}
      """.trimIndent() + "\n"
    }

    private fun getDevice(serialNumber: String) = devices[serialNumber] ?: throw AssertionError("Device $serialNumber not set up")
  }
}

/**
 * Remove the last [DeviceEvent] from the list asserting it is a [TrackingReset] event
 *
 * Since we stop the ADB server, we expect the flow to emit a TrackingReset event, but we don't want to have to specify it every time.
 */
private fun <E> MutableList<E>.dropTrackingReset(): List<E> {
  assertThat(last()).isInstanceOf(TrackingReset::class.java)
  return dropLast(1)
}

private fun FakeAdbRule.attachDevices(vararg devices: TestDevice): List<DeviceState> = devices.map(::attachDevice)

private fun FakeAdbRule.attachDevice(device: TestDevice): DeviceState {
  // Since we handle the response to the getprop commands ourselves, we don't really need to provide them to attachDevice
  val deviceState = attachDevice(device.serialNumber, manufacturer = "", model = "", release = "", sdk = "")
  if (!device.device.isOnline) {
    deviceState.deviceStatus = DeviceState.DeviceStatus.OFFLINE
  }
  return deviceState
}

private fun FakeAdbRule.createAdbSession(): AdbSession {
  val host = TestingAdbSessionHost()
  val channelProvider = AdbChannelProviderFactory.createOpenLocalHost(host) { fakeAdbServerPort }
  return AdbSession.create(host, channelProvider)
}

suspend fun yieldUntil(
  timeout: Duration = Duration.ofSeconds(5),
  predicate: suspend () -> Boolean
) {
  try {
    withTimeout(timeout.toMillis()) {
      while (!predicate()) {
        yield()
      }
    }
  }
  catch (e: TimeoutCancellationException) {
    throw AssertionError(
      "A yieldUntil condition was not satisfied within " +
      "5 seconds, there is a bug somewhere (in the test or in the tested code)", e
    )
  }
}
