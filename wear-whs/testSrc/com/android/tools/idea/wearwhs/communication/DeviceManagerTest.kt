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

import com.android.adblib.ClosedSessionException
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.WhsDataValue
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

const val WHS_PACKAGE_ID = "com.google.android.wearable.healthservices"
const val WHS_CONTENT_PROVIDER_URI = "content://$WHS_PACKAGE_ID.dev.synthetic/synthetic_config"
const val CONTENT_UPDATE_SHELL_COMMAND = "content update --uri $WHS_CONTENT_PROVIDER_URI"

class DeviceManagerTest {

  private var adbSession = FakeAdbSession()
  private var adbSessionProvider = { adbSession }
  private val serialNumber: String = "12345"

  @Before fun setUp() = runBlocking { adbSession.hostServices.connect(DeviceAddress(serialNumber)) }

  @After
  fun tearDown() = runBlocking {
    try {
      adbSession.hostServices.disconnect(DeviceAddress(serialNumber))
    } catch (_: ClosedSessionException) {
      // Already closed during test, ignore
    }
  }

  @Test
  fun `test adb commands are not sent if the device is disconnected`() = runBlocking {
    adbSession.hostServices.disconnect(DeviceAddress(serialNumber))

    val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
    deviceManager.setSerialNumber(serialNumber)

    assertFailure(deviceManager.clearContentProvider())
    assertThat(adbSession.deviceServices.shellV2Requests).isEmpty()
  }

  @Test
  fun `test setCapabilities throws connection lost exception when adb session is closed`() =
    runBlocking {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      deviceManager.setSerialNumber(serialNumber)

      adbSession.close()

      assertFailure(deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to true)))
    }

  @Test
  fun `test overrideValues throws connection lost exception when adb session is closed`() =
    runBlocking {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      deviceManager.setSerialNumber(serialNumber)

      adbSession.close()

      assertFailure(deviceManager.overrideValues(listOf(WhsDataType.STEPS.value(50))))
    }

  @Test
  fun `test loadActiveExercise throws connection lost exception when adb session is closed`() =
    runBlocking {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      deviceManager.setSerialNumber(serialNumber)

      adbSession.close()

      assertFailure(deviceManager.loadActiveExercise())
    }

  @Test
  fun `test triggerEvent throws connection lost exception when adb session is closed`() =
    runBlocking {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      deviceManager.setSerialNumber(serialNumber)

      adbSession.close()

      assertFailure(deviceManager.triggerEvent(EventTrigger("whs.TEST", "test")))
    }

  @Test
  fun `test loadCurrentCapabilityStates throws connection lost exception when adb session is closed`() =
    runBlocking {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      deviceManager.setSerialNumber(serialNumber)

      adbSession.close()

      assertFailure(deviceManager.loadCurrentCapabilityStates())
    }

  @Test
  fun `enabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

    val job = launch { deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to true)) }
    job.join()
  }

  @Test
  fun `disabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

    val job = launch { deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to false)) }
    job.join()
  }

  private fun assertDeviceManagerFunctionSendsAdbCommand(
    func: suspend (WearHealthServicesDeviceManager) -> Unit,
    expectedAdbCommand: String,
    deviceManager: ContentProviderDeviceManager = ContentProviderDeviceManager(adbSessionProvider),
    adbSession: FakeAdbSession = this.adbSession,
  ) = runTest {
    adbSession.deviceServices.configureShellCommand(
      DeviceSelector.fromSerialNumber(serialNumber),
      expectedAdbCommand,
      "",
    )

    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    val job = launch { func(deviceManager) }
    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(expectedAdbCommand, shellRequest.command)
  }

  private fun assertEnablingCapabilitySendsAdbCommand(
    dataType: WhsDataType,
    expectedAdbCommand: String,
  ) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.setCapabilities(mapOf(dataType to true)) },
      expectedAdbCommand,
    )
  }

  private fun assertDisablingCapabilitySendsAdbCommand(
    dataType: WhsDataType,
    expectedAdbCommand: String,
  ) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.setCapabilities(mapOf(dataType to false)) },
      expectedAdbCommand,
    )
  }

  @Test
  fun `enable and disable steps`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.STEPS,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.STEPS,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:b:false",
    )
  }

  @Test
  fun `enable and disable distance`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.DISTANCE,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.DISTANCE,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:b:false",
    )
  }

  @Test
  fun `enable and disable calories`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.CALORIES,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.CALORIES,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:b:false",
    )
  }

  @Test
  fun `enable and disable floors`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.FLOORS,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.FLOORS,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:b:false",
    )
  }

  @Test
  fun `enable and disable elevation gain`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.ELEVATION_GAIN,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.ELEVATION_GAIN,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:b:false",
    )
  }

  @Test
  fun `enable and disable elevation loss`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.ELEVATION_LOSS,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.ELEVATION_LOSS,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:b:false",
    )
  }

  @Test
  fun `enable and disable absolute elevation`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.ABSOLUTE_ELEVATION,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.ABSOLUTE_ELEVATION,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:b:false",
    )
  }

  @Test
  fun `enable and disable location`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.LOCATION,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind LOCATION:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.LOCATION,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind LOCATION:b:false",
    )
  }

  @Test
  fun `enable and disable heart rate bpm`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.HEART_RATE_BPM,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.HEART_RATE_BPM,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:b:false",
    )
  }

  @Test
  fun `enable and disable speed`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.SPEED,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.SPEED,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:b:false",
    )
  }

  @Test
  fun `enable and disable pace`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.PACE,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.PACE,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:b:false",
    )
  }

  @Test
  fun `enable and disable steps per minute`() {
    assertEnablingCapabilitySendsAdbCommand(
      WhsDataType.STEPS_PER_MINUTE,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:b:true",
    )
    assertDisablingCapabilitySendsAdbCommand(
      WhsDataType.STEPS_PER_MINUTE,
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:b:false",
    )
  }

  @Test
  fun `setting capability override value when serial number is not set does not result in crash`() =
    runTest {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

      val job = launch { deviceManager.overrideValues(listOf(WhsDataType.STEPS.value(55))) }
      job.join()
    }

  private fun assertOverrideSendsAdbCommand(
    overrideValue: WhsDataValue,
    expectedAdbCommand: String,
  ) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.overrideValues(listOf(overrideValue)) },
      expectedAdbCommand,
    )
  }

  @Test
  fun `override steps`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.STEPS.value(55),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:i:55",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.STEPS.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS:s:\"\"",
    )
  }

  @Test
  fun `override distance`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.DISTANCE.value(10f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:f:10.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.DISTANCE.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind DISTANCE:s:\"\"",
    )
  }

  @Test
  fun `override calories`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.CALORIES.value(100f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:f:100.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.CALORIES.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:s:\"\"",
    )
  }

  @Test
  fun `override floors`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.FLOORS.value(5f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:f:5.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.FLOORS.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind FLOORS:s:\"\"",
    )
  }

  @Test
  fun `override elevation gain`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.ELEVATION_GAIN.value(50f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:f:50.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.ELEVATION_GAIN.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_GAIN:s:\"\"",
    )
  }

  @Test
  fun `override elevation loss`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.ELEVATION_LOSS.value(20f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:20.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.ELEVATION_LOSS.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:s:\"\"",
    )
  }

  @Test
  fun `override absolute elevation`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.ABSOLUTE_ELEVATION.value(120f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:f:120.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.ABSOLUTE_ELEVATION.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:s:\"\"",
    )
  }

  @Test
  fun `override heart rate bpm`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.HEART_RATE_BPM.value(65f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:f:65.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.HEART_RATE_BPM.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind HEART_RATE_BPM:s:\"\"",
    )
  }

  @Test
  fun `override speed`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.SPEED.value(30f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:f:30.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.SPEED.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind SPEED:s:\"\"",
    )
  }

  @Test
  fun `override pace`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.PACE.value(20f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:f:20.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.PACE.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind PACE:s:\"\"",
    )
  }

  @Test
  fun `override steps per minute`() {
    assertOverrideSendsAdbCommand(
      WhsDataType.STEPS_PER_MINUTE.value(25f),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:f:25.0",
    )
    assertOverrideSendsAdbCommand(
      WhsDataType.STEPS_PER_MINUTE.noValue(),
      "$CONTENT_UPDATE_SHELL_COMMAND --bind STEPS_PER_MINUTE:s:\"\"",
    )
  }

  @Test
  fun `trigger auto pause event`() = runBlocking {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.triggerEvent(EventTrigger("whs.AUTO_PAUSE_DETECTED", "label"))
      },
      "am broadcast -a \"whs.AUTO_PAUSE_DETECTED\" com.google.android.wearable.healthservices",
    )
  }

  @Test
  fun `trigger golf shot event`() = runBlocking {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.triggerEvent(EventTrigger("whs.GOLF_SHOT", "label")) },
      "am broadcast -a \"whs.GOLF_SHOT\" com.google.android.wearable.healthservices",
    )
  }

  @Test
  fun `trigger full swing golf shot event`() = runBlocking {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.triggerEvent(
          EventTrigger("whs.GOLF_SHOT", "label", mapOf("golf_shot_swing_type" to "full"))
        )
      },
      "am broadcast -a \"whs.GOLF_SHOT\" --es golf_shot_swing_type \"full\" com.google.android.wearable.healthservices",
    )
  }

  @Test
  fun `clear content provider without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

    val job = launch { deviceManager.clearContentProvider() }
    job.join()
  }

  @Test
  fun `clear content provider triggers correct adb command`() {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager -> deviceManager.clearContentProvider() },
      "content delete --uri $WHS_CONTENT_PROVIDER_URI",
    )
  }

  @Test
  fun `setting multiple capabilities without setting serial number does not result in crash`() =
    runTest {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

      val job = launch { deviceManager.setCapabilities(mapOf(WhsDataType.STEPS to true)) }
      job.join()
    }

  @Test
  fun `setting multiple capabilities triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.setCapabilities(
          mapOf(
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
          )
        )
      },
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ABSOLUTE_ELEVATION:b:false --bind CALORIES:b:false --bind DISTANCE:b:false --bind ELEVATION_GAIN:b:true --bind ELEVATION_LOSS:b:false --bind FLOORS:b:false --bind HEART_RATE_BPM:b:true --bind LOCATION:b:true --bind PACE:b:true --bind SPEED:b:false --bind STEPS:b:true --bind STEPS_PER_MINUTE:b:false",
    )
  }

  @Test
  fun `setting multiple override values without setting serial number does not result in crash`() =
    runTest {
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

      val job = launch { deviceManager.overrideValues(listOf(WhsDataType.STEPS.value(55f))) }
      job.join()
    }

  @Test
  fun `setting multiple float override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.overrideValues(
          listOf(
            WhsDataType.DISTANCE.value(12.0f),
            WhsDataType.CALORIES.value(123.0f),
            WhsDataType.FLOORS.value(5.0f),
          )
        )
      },
      "$CONTENT_UPDATE_SHELL_COMMAND --bind CALORIES:f:123.0 --bind DISTANCE:f:12.0 --bind FLOORS:f:5.0",
    )
  }

  @Test
  fun `setting float and int override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.overrideValues(
          listOf(WhsDataType.STEPS.value(55), WhsDataType.ELEVATION_LOSS.value(5.0f))
        )
      },
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:5.0 --bind STEPS:i:55",
    )
  }

  @Test
  fun `setting float, int and null override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.overrideValues(
          listOf(
            WhsDataType.STEPS.value(55),
            WhsDataType.ELEVATION_LOSS.value(5.0f),
            WhsDataType.PACE.noValue(),
          )
        )
      },
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:5.0 --bind PACE:s:\"\" --bind STEPS:i:55",
    )
  }

  @Test
  fun `setting location override value is ignored`() {
    assertDeviceManagerFunctionSendsAdbCommand(
      { deviceManager ->
        deviceManager.overrideValues(
          listOf(
            WhsDataType.STEPS.value(55),
            WhsDataType.ELEVATION_LOSS.value(5.0f),
            WhsDataType.PACE.noValue(),
            WhsDataType.LOCATION.noValue(),
          )
        )
      },
      "$CONTENT_UPDATE_SHELL_COMMAND --bind ELEVATION_LOSS:f:5.0 --bind PACE:s:\"\" --bind STEPS:i:55",
    )
  }

  private fun assertExerciseCommandParsesResultsCorrectly(response: String, expected: Boolean) =
    runTest {
      val queryExerciseStateCommand =
        "content query --uri content://com.google.android.wearable.healthservices.dev.exerciseinfo"
      adbSession.deviceServices.configureShellCommand(
        DeviceSelector.fromSerialNumber(serialNumber),
        queryExerciseStateCommand,
        response,
      )
      val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
      deviceManager.setSerialNumber(serialNumber)

      val previousCount = adbSession.deviceServices.shellV2Requests.size

      var isSupported = false
      val job = launch { isSupported = deviceManager.loadActiveExercise().getOrThrow() }
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
  fun `load active exercise returns true when exercise is active`() =
    assertExerciseCommandParsesResultsCorrectly("Row: 0 active_exercise=true", true)

  @Test
  fun `load active exercise returns false when exercise is not active`() =
    assertExerciseCommandParsesResultsCorrectly("Row: 0 active_exercise=false", false)

  @Test
  fun `load active exercise returns false when response is unexpected`() =
    assertExerciseCommandParsesResultsCorrectly("This is not supposed to happen", false)

  @Test
  fun `loading capabilities without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSessionProvider)

    val job = launch { deviceManager.getCapabilities() }
    job.join()
  }

  private fun assertLoadCapabilitiesAdbResponseIsParsedCorrectly(
    response: String,
    expectedCapabilites: Map<WhsDataType, CapabilityState>,
  ) = runTest {
    val queryContentProviderCommand = "content query --uri $WHS_CONTENT_PROVIDER_URI"
    adbSession.deviceServices.configureShellCommand(
      DeviceSelector.fromSerialNumber(serialNumber),
      queryContentProviderCommand,
      response,
    )

    val deviceManager = ContentProviderDeviceManager(adbSessionProvider)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    var parsedCapabilities: Map<WhsDataType, CapabilityState> =
      WHS_CAPABILITIES.associate { it.dataType to CapabilityState.disabled(it.dataType) }
    val job = launch {
      parsedCapabilities = deviceManager.loadCurrentCapabilityStates().getOrThrow()
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
    // Content provider does not return rows for dataTypes which are enabled but override values are
    // not set
    // Content provider returns override value of 0 if dataType is disabled but override value has
    // not been set
    // In all other cases enabled state and override values should be parsed as seen in table
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly(
      """
                                                       Row: 0 data_type=STEPS_PER_MINUTE, is_enabled=false, override_value=0.0
                                                       Row: 1 data_type=SPEED, is_enabled=true, override_value=0.0
                                                       Row: 2 data_type=FLOORS, is_enabled=false, override_value=5.0
                                                       Row: 3 data_type=ABSOLUTE_ELEVATION, is_enabled=false, override_value=0.0
                                                       Row: 4 data_type=ELEVATION_LOSS, is_enabled=false, override_value=0.0
                                                       Row: 5 data_type=DISTANCE, is_enabled=true, override_value=0.0
                                                       Row: 6 data_type=ELEVATION_GAIN, is_enabled=false, override_value=0.0
                                                       Row: 7 data_type=CALORIES, is_enabled=false, override_value=0.0
                                                       Row: 8 data_type=PACE, is_enabled=false, override_value=0.0
                                                       Row: 9 data_type=HEART_RATE_BPM, is_enabled=true, override_value=55.0
                                                       Row: 10 data_type=STEPS, is_enabled=true, override_value=42
                                                       """
        .trimIndent(),
      mapOf(
        WhsDataType.STEPS_PER_MINUTE to CapabilityState.disabled(WhsDataType.STEPS_PER_MINUTE),
        WhsDataType.SPEED to CapabilityState(true, WhsDataType.SPEED.value(0f)),
        WhsDataType.FLOORS to CapabilityState(false, WhsDataType.FLOORS.value(5f)),
        WhsDataType.ABSOLUTE_ELEVATION to CapabilityState.disabled(WhsDataType.ABSOLUTE_ELEVATION),
        WhsDataType.ELEVATION_LOSS to CapabilityState.disabled(WhsDataType.ELEVATION_LOSS),
        WhsDataType.DISTANCE to CapabilityState(true, WhsDataType.DISTANCE.value(0f)),
        WhsDataType.ELEVATION_GAIN to CapabilityState.disabled(WhsDataType.ELEVATION_GAIN),
        WhsDataType.CALORIES to CapabilityState.disabled(WhsDataType.CALORIES),
        WhsDataType.PACE to CapabilityState.disabled(WhsDataType.PACE),
        WhsDataType.HEART_RATE_BPM to CapabilityState(true, WhsDataType.HEART_RATE_BPM.value(55f)),
        WhsDataType.STEPS to CapabilityState(true, WhsDataType.STEPS.value(42)),
      ),
    )
  }

  @Test
  fun `float capabilities are parsed correctly`() {
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly(
      """
         Row: 0 data_type=HEART_RATE_BPM, is_enabled=true, override_value=554E2
         Row: 1 data_type=STEPS, is_enabled=true, override_value=42
       """
        .trimIndent(),
      mapOf(
        WhsDataType.HEART_RATE_BPM to
          CapabilityState(true, WhsDataType.HEART_RATE_BPM.value(55400.0f)),
        WhsDataType.STEPS to CapabilityState(true, WhsDataType.STEPS.value(42)),
      ),
    )
  }

  @Test
  fun `unknown data type capabilities are ignored`() {
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly(
      """
                                                       Row: 0 data_type=DATA_TYPE_UNKNOWN, is_enabled=true, override_value=0
                                                       Row: 1 data_type=STEPS, is_enabled=true, override_value=0
                                                       Row: 2 data_type=DATA_TYPE_UNKNOWN, is_enabled=true, override_value=0
                                                       """
        .trimIndent(),
      mapOf(WhsDataType.STEPS to CapabilityState(true, WhsDataType.STEPS.value(0))),
    )
  }

  @Test
  fun `device manager stores and uses the same adbSession if it's not closed`() =
    runBlocking<Unit> {
      var currentAdbSession = adbSession
      val deviceManager = ContentProviderDeviceManager({ currentAdbSession })
      deviceManager.setSerialNumber(serialNumber)

      assertDeviceManagerFunctionSendsAdbCommand(
        { it.clearContentProvider() },
        "content delete --uri $WHS_CONTENT_PROVIDER_URI",
        deviceManager,
        currentAdbSession,
      )

      val previousAdbSession = currentAdbSession

      // Create and close a new adb session
      currentAdbSession = FakeAdbSession()
      currentAdbSession.close()

      assertDeviceManagerFunctionSendsAdbCommand(
        { it.clearContentProvider() },
        "content delete --uri $WHS_CONTENT_PROVIDER_URI",
        deviceManager,
        previousAdbSession,
      )
    }

  @Test
  fun `device manager throws exception if the adbSession is closed and the new one is closed too`() =
    runBlocking<Unit> {
      var currentAdbSession = adbSession
      val deviceManager = ContentProviderDeviceManager({ currentAdbSession })
      deviceManager.setSerialNumber(serialNumber)

      assertDeviceManagerFunctionSendsAdbCommand(
        { it.clearContentProvider() },
        "content delete --uri $WHS_CONTENT_PROVIDER_URI",
        deviceManager,
        currentAdbSession,
      )

      // Close the current adb session
      currentAdbSession.close()

      // Create and close a new adb session
      currentAdbSession = FakeAdbSession()
      currentAdbSession.close()

      assertFailure(deviceManager.clearContentProvider())
    }

  @Test
  fun `device manager returns failure if the adbSession is closed and the new one is not created`() =
    runBlocking<Unit> {
      val currentAdbSession = adbSession
      val deviceManager = ContentProviderDeviceManager({ currentAdbSession })
      deviceManager.setSerialNumber(serialNumber)

      assertDeviceManagerFunctionSendsAdbCommand(
        { it.clearContentProvider() },
        "content delete --uri $WHS_CONTENT_PROVIDER_URI",
        deviceManager,
        currentAdbSession,
      )

      // Close the current adb session
      currentAdbSession.close()

      assertFailure(deviceManager.clearContentProvider())
    }

  @Test
  fun `device manager creates a new adbSession if the current one is closed`() = runBlocking {
    var currentAdbSession = adbSession
    val deviceManager = ContentProviderDeviceManager({ currentAdbSession })
    deviceManager.setSerialNumber(serialNumber)

    assertDeviceManagerFunctionSendsAdbCommand(
      { it.clearContentProvider() },
      "content delete --uri $WHS_CONTENT_PROVIDER_URI",
      deviceManager,
      currentAdbSession,
    )

    // Close the current adb session
    currentAdbSession.close()

    // Create and redirect to the new adb session
    val newAdbSession = FakeAdbSession().apply { hostServices.connect(DeviceAddress(serialNumber)) }
    currentAdbSession = newAdbSession

    assertDeviceManagerFunctionSendsAdbCommand(
      { it.clearContentProvider() },
      "content delete --uri $WHS_CONTENT_PROVIDER_URI",
      deviceManager,
      newAdbSession,
    )
  }

  @Test
  fun `disabled capabilities of a known type that don't have a value are parsed correctly`() {
    assertLoadCapabilitiesAdbResponseIsParsedCorrectly(
      """
       Row: 0 data_type=LOCATION, is_enabled=false, override_value=0
      """
        .trimIndent(),
      mapOf(WhsDataType.LOCATION to CapabilityState.disabled(WhsDataType.LOCATION)),
    )
  }

  private fun <T> assertFailure(result: Result<T>) {
    assertThat(result.isFailure).isTrue()
  }
}
