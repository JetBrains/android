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
package com.android.tools.idea.wearwhs.communication

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsDataType
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

const val WHS_PACKAGE_ID = "com.google.android.wearable.healthservices"
const val WHS_CONTENT_PROVIDER_URI = "content://$WHS_PACKAGE_ID.dev.synthetic/synthetic_config"
const val CONTENT_UPDATE_SHELL_COMMAND = "content update --uri $WHS_CONTENT_PROVIDER_URI"

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagerTest {

  private lateinit var adbSession: FakeAdbSession
  private val serialNumber: String = "1234"

  @Before
  fun setUp() {
    adbSession = FakeAdbSession()
    adbSession.throwIfClosed()
  }

  @Test
  fun `test setCapabilities throws connection lost exception when adb session is closed`() {
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    adbSession.close()

    assertThrows(ConnectionLostException::class.java) {
      runBlocking {
        deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to true))
      }
    }
  }

  @Test
  fun `test overrideValues throws connection lost exception when adb session is closed`() {
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    adbSession.close()

    assertThrows(ConnectionLostException::class.java) {
      runBlocking {
        deviceManager.overrideValues(mapOf(WhsDataType.STEPS to 50))
      }
    }
  }

  @Test
  fun `test loadActiveExercise throws connection lost exception when adb session is closed`() {
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    adbSession.close()

    assertThrows(ConnectionLostException::class.java) {
      runBlocking {
        deviceManager.loadActiveExercise()
      }
    }
  }

  @Test
  fun `test triggerEvent throws connection lost exception when adb session is closed`() {
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    adbSession.close()

    assertThrows(ConnectionLostException::class.java) {
      runBlocking {
        deviceManager.triggerEvent(EventTrigger("whs.TEST", "test"))
      }
    }
  }

  @Test
  fun `test loadCurrentCapabilityStates throws connection lost exception when adb session is closed`() {
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    adbSession.close()

    assertThrows(ConnectionLostException::class.java) {
      runBlocking {
        deviceManager.loadCurrentCapabilityStates()
      }
    }
  }

  @Test
  fun `test isWhsVersionSupported throws connection lost exception when adb session is closed`() {
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    adbSession.close()

    assertThrows(ConnectionLostException::class.java) {
      runBlocking {
        deviceManager.isWhsVersionSupported()
      }
    }
  }

  @Test
  fun `enabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to true))
    }
    job.join()
  }

  @Test
  fun `disabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to false))
    }
    job.join()
  }

  private fun assertDeviceManagerFunctionSendsAdbCommand(func: suspend (WearHealthServicesDeviceManager) -> Unit,
                                                         expectedAdbCommand: String) = runTest {
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), expectedAdbCommand, "")

    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    val job = launch {
      func(deviceManager)
    }
    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(expectedAdbCommand, shellRequest.command)
  }

  private fun assertEnablingCapabilitySendsAdbCommand(dataType: WhsDataType, expectedAdbCommand: String) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.setCapabilities(mapOf(dataType to true)) },
                                               expectedAdbCommand)
  }

  private fun assertDisablingCapabilitySendsAdbCommand(dataType: WhsDataType, expectedAdbCommand: String) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.setCapabilities(mapOf(dataType to false)) },
                                               expectedAdbCommand)
  }

  @Test
  fun `enable and disable steps`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.STEPS, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.STEPS, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:b:false")
  }

  @Test
  fun `enable and disable distance`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.DISTANCE, "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.DISTANCE, "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:b:false")
  }

  @Test
  fun `enable and disable calories`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.CALORIES, "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.CALORIES, "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:b:false")
  }

  @Test
  fun `enable and disable floors`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.FLOORS, "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.FLOORS, "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:b:false")
  }

  @Test
  fun `enable and disable elevation gain`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_GAIN, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_GAIN, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:b:false")
  }

  @Test
  fun `enable and disable elevation loss`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_LOSS, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_LOSS, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:b:false")
  }

  @Test
  fun `enable and disable absolute elevation`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:b:false")
  }

  @Test
  fun `enable and disable location`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.LOCATION, "$CONTENT_UPDATE_SHELL_COMMAND --bind LOCATION:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.LOCATION, "$CONTENT_UPDATE_SHELL_COMMAND --bind LOCATION:b:false")
  }

  @Test
  fun `enable and disable heart rate bpm`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.HEART_RATE_BPM, "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.HEART_RATE_BPM, "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:b:false")
  }

  @Test
  fun `enable and disable speed`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.SPEED, "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.SPEED, "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:b:false")
  }

  @Test
  fun `enable and disable pace`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.PACE, "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.PACE, "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:b:false")
  }

  @Test
  fun `enable and disable steps per minute`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:b:true")
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:b:false")
  }

  @Test
  fun `setting capability override value when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.overrideValues(mapOf(WhsDataType.STEPS to 55))
    }
    job.join()
  }

  private fun assertOverrideSendsAdbCommand(dataType: WhsDataType, overrideValue: Number?, expectedAdbCommand: String) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValues(mapOf(dataType to overrideValue)) },
                                               expectedAdbCommand)
  }

  @Test
  fun `override steps`() {
    assertOverrideSendsAdbCommand(WhsDataType.STEPS, 55, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:i:55")
    assertOverrideSendsAdbCommand(WhsDataType.STEPS, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:s:\"\"")
  }

  @Test
  fun `override distance`() {
    assertOverrideSendsAdbCommand(WhsDataType.DISTANCE, 10, "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:f:10.0")
    assertOverrideSendsAdbCommand(WhsDataType.DISTANCE, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:s:\"\"")
  }

  @Test
  fun `override calories`() {
    assertOverrideSendsAdbCommand(WhsDataType.CALORIES, 100, "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:f:100.0")
    assertOverrideSendsAdbCommand(WhsDataType.CALORIES, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:s:\"\"")
  }

  @Test
  fun `override floors`() {
    assertOverrideSendsAdbCommand(WhsDataType.FLOORS, 5, "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:f:5.0")
    assertOverrideSendsAdbCommand(WhsDataType.FLOORS, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:s:\"\"")
  }

  @Test
  fun `override elevation gain`() {
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_GAIN, 50, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:f:50.0")
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_GAIN, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:s:\"\"")
  }

  @Test
  fun `override elevation loss`() {
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_LOSS, 20, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:20.0")
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_LOSS, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:s:\"\"")
  }

  @Test
  fun `override absolute elevation`() {
    assertOverrideSendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, 120, "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:f:120.0")
    assertOverrideSendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:s:\"\"")
  }

  @Test
  fun `override heart rate bpm`() {
    assertOverrideSendsAdbCommand(WhsDataType.HEART_RATE_BPM, 65, "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:f:65.0")
    assertOverrideSendsAdbCommand(WhsDataType.HEART_RATE_BPM, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:s:\"\"")
  }

  @Test
  fun `override speed`() {
    assertOverrideSendsAdbCommand(WhsDataType.SPEED, 30, "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:f:30.0")
    assertOverrideSendsAdbCommand(WhsDataType.SPEED, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:s:\"\"")
  }

  @Test
  fun `override pace`() {
    assertOverrideSendsAdbCommand(WhsDataType.PACE, 20, "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:f:20.0")
    assertOverrideSendsAdbCommand(WhsDataType.PACE, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:s:\"\"")
  }

  @Test
  fun `override steps per minute`() {
    assertOverrideSendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, 25, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:f:25.0")
    assertOverrideSendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, null, "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:s:\"\"")
  }

  @Test
  fun `trigger auto pause event`() = runBlocking {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.triggerEvent(EventTrigger("whs.AUTO_PAUSE_DETECTED", "label")) },
      "am broadcast -a \"whs.AUTO_PAUSE_DETECTED\" com.google.android.wearable.healthservices")
  }

  @Test
  fun `trigger golf shot event`() = runBlocking {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.triggerEvent(EventTrigger("whs.GOLF_SHOT", "label")) },
      "am broadcast -a \"whs.GOLF_SHOT\" com.google.android.wearable.healthservices")
  }

  @Test
  fun `trigger full swing golf shot event`() = runBlocking {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.triggerEvent(
          EventTrigger(
            "whs.GOLF_SHOT",
            "label",
            mapOf("golf_shot_swing_type" to "full")))
      },
      "am broadcast -a \"whs.GOLF_SHOT\" --es golf_shot_swing_type \"full\" com.google.android.wearable.healthservices")
  }

  @Test
  fun `clear content provider without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.clearContentProvider()
    }
    job.join()
  }

  @Test
  fun `clear content provider triggers correct adb command`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.clearContentProvider() },
                                               "content delete --uri $WHS_CONTENT_PROVIDER_URI")
  }

  @Test
  fun `setting multiple capabilities without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to true))
    }
    job.join()
  }

  @Test
  fun `setting multiple capabilities triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager ->
                                                 deviceManager.setCapabilities(mapOf(
                                                   WhsDataType.STEPS to true,
                                                   WhsDataType.DISTANCE to false,
                                                   WhsDataType.CALORIES to false,
                                                   WhsDataType.FLOORS to false,
                                                   WhsDataType.ELEVATION_GAIN to true,
                                                   WhsDataType.ELEVATION_LOSS to false,
                                                   WhsDataType.ABSOLUTE_ELEVATION to false,
                                                   WhsDataType.LOCATION to true,
                                                   WhsDataType.HEART_RATE_BPM to true,
                                                   WhsDataType.SPEED to false,
                                                   WhsDataType.PACE to true,
                                                   WhsDataType.STEPS_PER_MINUTE to false,
                                                 ))
                                               },
                                               "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:b:false --bind CALORIES:b:false --bind DISTANCE:b:false --bind ELEVATION_GAIN:b:true --bind ELEVATION_LOSS:b:false --bind FLOORS:b:false --bind HEART_RATE_BPM:b:true --bind LOCATION:b:true --bind PACE:b:true --bind SPEED:b:false --bind STEPS:b:true --bind STEPS_PER_MINUTE:b:false")
  }

  @Test
  fun `setting multiple override values without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.overrideValues(mapOf(WhsDataType.STEPS to 55))
    }
    job.join()
  }

  @Test
  fun `setting multiple float override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager ->
                                                 deviceManager.overrideValues(mapOf(
                                                   WhsDataType.DISTANCE to 12.0,
                                                   WhsDataType.CALORIES to 123.0,
                                                   WhsDataType.FLOORS to 5.0,
                                                 ))
                                               },
                                               "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:f:123.0 --bind DISTANCE:f:12.0 --bind FLOORS:f:5.0")
  }

  @Test
  fun `setting float and int override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager ->
                                                 deviceManager.overrideValues(mapOf(
                                                   WhsDataType.STEPS to 55,
                                                   WhsDataType.ELEVATION_LOSS to 5.0,
                                                 ))
                                               }, "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:5.0 --bind STEPS:i:55")
  }

  @Test
  fun `setting float, int and null override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager ->
                                                 deviceManager.overrideValues(mapOf(
                                                   WhsDataType.STEPS to 55,
                                                   WhsDataType.ELEVATION_LOSS to 5.0,
                                                   WhsDataType.PACE to null,
                                                 ))
                                               },
                                               "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:5.0 --bind PACE:s:\"\" --bind STEPS:i:55")
  }

  @Test
  fun `setting location override value is ignored`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager ->
                                                 deviceManager.overrideValues(mapOf(
                                                   WhsDataType.STEPS to 55,
                                                   WhsDataType.ELEVATION_LOSS to 5.0,
                                                   WhsDataType.PACE to null,
                                                   WhsDataType.LOCATION to null,
                                                 ))
                                               },
                                               "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:5.0 --bind PACE:s:\"\" --bind STEPS:i:55")
  }

  @Test
  fun `checking is WHS version is supported without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.isWhsVersionSupported()
    }
    job.join()
  }

  private fun assertWhsVersionCheckAdbResponseIsParsedCorrectly(response: String, expectedIsSupportedBool: Boolean) = runTest {
    val checkWhsVersionCommand = "dumpsys package $WHS_PACKAGE_ID | grep versionCode | head -n1"
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), checkWhsVersionCommand, response)

    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    var isSupported = false
    val job = launch {
      isSupported = deviceManager.isWhsVersionSupported()
    }

    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(checkWhsVersionCommand, shellRequest.command)
    assertEquals(expectedIsSupportedBool, isSupported)
  }

  @Test
  fun `unexpected ADB response results in WHS version being reported as unsupported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("Unexpected response", false)
  }

  @Test
  fun `dev whs version code is supported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("    versionCode=1 minSdk=30 targetSdk=33", true)
  }

  @Test
  fun `minimum whs version code is supported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("    versionCode=1447606 minSdk=30 targetSdk=33", true)
  }

  @Test
  fun `whs version codes higher than minimum are supported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("    versionCode=1448000 minSdk=30 targetSdk=33", true)
  }

  @Test
  fun `whs version codes lower than minimum are not supported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("    versionCode=1417661 minSdk=30 targetSdk=33", false)
  }

  private fun assertExerciseCommandParsesResultsCorrectly(response: String, expected: Boolean) = runTest {
    val queryExerciseStateCommand = "content query --uri content://com.google.android.wearable.healthservices.dev.exerciseinfo"
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), queryExerciseStateCommand,
                                                    response)
    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    var isSupported = false
    val job = launch {
      isSupported = deviceManager.loadActiveExercise()
    }
    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(queryExerciseStateCommand, shellRequest.command)

    assertEquals(expected, isSupported)
  }

  @Test
  fun `load active exercise returns true when exercise is active`() = assertExerciseCommandParsesResultsCorrectly(
    "Row: 0 active_exercise=true", true)

  @Test
  fun `load active exercise returns false when exercise is not active`() = assertExerciseCommandParsesResultsCorrectly(
    "Row: 0 active_exercise=false", false)

  @Test
  fun `load active exercise returns false when response is unexpected`() = assertExerciseCommandParsesResultsCorrectly(
    "This is not supposed to happen", false)

  @Test
  fun `loading capabilities without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.loadCapabilities()
    }
    job.join()
  }

  private fun assertLoadCapabilitiesAdbResponseIsParsedCorrectly(response: String, expectedCapabilites: Map<WhsDataType, CapabilityState>) = runTest {
    val queryContentProviderCommand = "content query --uri $WHS_CONTENT_PROVIDER_URI"
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), queryContentProviderCommand, response)

    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    var parsedCapabilities = WHS_CAPABILITIES.associate { it.dataType to CapabilityState(false, null) }
    val job = launch {
      parsedCapabilities = deviceManager.loadCurrentCapabilityStates()
    }
    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(queryContentProviderCommand, shellRequest.command)

    assertEquals(expectedCapabilites, parsedCapabilities)
  }

  @Test
  fun `unexpected ADB response results in no capabilities being reported`() {
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly("Unexpected response", emptyMap())
  }

  @Test
  fun `enabled state of capabilities are parsed, override values are ignored`() {
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly("""
                                                       Row: 0 data_type=STEPS_PER_MINUTE, is_enabled=false, override_value=0.0
                                                       Row: 1 data_type=SPEED, is_enabled=true, override_value=0.0
                                                       Row: 2 data_type=FLOORS, is_enabled=false, override_value=0.0
                                                       Row: 3 data_type=ABSOLUTE_ELEVATION, is_enabled=false, override_value=0.0
                                                       Row: 4 data_type=ELEVATION_LOSS, is_enabled=false, override_value=0.0
                                                       Row: 5 data_type=DISTANCE, is_enabled=true, override_value=0.0
                                                       Row: 6 data_type=ELEVATION_GAIN, is_enabled=false, override_value=0.0
                                                       Row: 7 data_type=CALORIES, is_enabled=false, override_value=0.0
                                                       Row: 8 data_type=PACE, is_enabled=false, override_value=0.0
                                                       Row: 9 data_type=HEART_RATE_BPM, is_enabled=true, override_value=55.0
                                                       Row: 10 data_type=STEPS, is_enabled=true, override_value=0
                                                       """.trimIndent(),
                                                       mapOf(
                                                        WhsDataType.STEPS_PER_MINUTE to CapabilityState(false, null),
                                                        WhsDataType.SPEED to CapabilityState(true, null),
                                                        WhsDataType.FLOORS to CapabilityState(false, null),
                                                        WhsDataType.ABSOLUTE_ELEVATION to CapabilityState(false, null),
                                                        WhsDataType.ELEVATION_LOSS to CapabilityState(false, null),
                                                        WhsDataType.DISTANCE to CapabilityState(true, null),
                                                        WhsDataType.ELEVATION_GAIN to CapabilityState(false, null),
                                                        WhsDataType.CALORIES to CapabilityState(false, null),
                                                        WhsDataType.PACE to CapabilityState(false, null),
                                                        WhsDataType.HEART_RATE_BPM to CapabilityState(true, null),
                                                        WhsDataType.STEPS to CapabilityState(true, null),
                                                       ))
  }

  @Test
  fun `unknown data type capabilities are ignored`() {
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly(
      """
                                                       Row: 0 data_type=DATA_TYPE_UNKNOWN, is_enabled=true, override_value=0
                                                       Row: 1 data_type=STEPS, is_enabled=true, override_value=0
                                                       Row: 2 data_type=DATA_TYPE_UNKNOWN, is_enabled=true, override_value=0
                                                       """.trimIndent(),
                                                       mapOf(
                                                        WhsDataType.STEPS to CapabilityState(true, null),
                                                      ))
  }
}
