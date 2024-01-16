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
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagerTest {
  private val adbCommandActiveExercise = "content query --uri content://com.google.android.wearable.healthservices.dev.exerciseinfo"
  private val adbCommandEnableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:true"
  private val adbCommandEnableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:true"
  private val adbCommandEnableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind CALORIES:b:true"
  private val adbCommandEnableFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:b:true"
  private val adbCommandEnableElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:b:true"
  private val adbCommandEnableElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:b:true"
  private val adbCommandEnableAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:true"
  private val adbCommandEnableLocation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind LOCATION:b:true"
  private val adbCommandEnableHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:b:true"
  private val adbCommandEnableSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:b:true"
  private val adbCommandEnablePace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:b:true"
  private val adbCommandEnableStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:b:true"
  private val adbCommandDisableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:false"
  private val adbCommandDisableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:false"
  private val adbCommandDisableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind CALORIES:b:false"
  private val adbCommandDisableFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:b:false"
  private val adbCommandDisableElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:b:false"
  private val adbCommandDisableElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:b:false"
  private val adbCommandDisableAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:false"
  private val adbCommandDisableLocation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind LOCATION:b:false"
  private val adbCommandDisableHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:b:false"
  private val adbCommandDisableSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:b:false"
  private val adbCommandDisablePace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:b:false"
  private val adbCommandDisableStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:b:false"
  private val adbCommandSetStepsTo55 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:i:55"
  private val adbCommandSetDistanceTo10 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:f:10.0"
  private val adbCommandSetTotalCaloriesTo100 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind CALORIES:f:100.0"
  private val adbCommandSetFloorsTo5 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:f:5.0"
  private val adbCommandSetElevationGainTo50 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:f:50.0"
  private val adbCommandSetElevationLossTo20 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:f:20.0"
  private val adbCommandSetAbsoluteElevationTo120 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:f:120.0"
  private val adbCommandSetHeartRateBpmTo65 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:f:65.0"
  private val adbCommandSetSpeedTo30 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:f:30.0"
  private val adbCommandSetPaceTo20 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:f:20.0"
  private val adbCommandSetStepsPerMinuteTo25 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:f:25.0"
  private val adbCommandClearSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:s:\"\""
  private val adbCommandClearDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:s:\"\""
  private val adbCommandClearTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind CALORIES:s:\"\""
  private val adbCommandClearFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:s:\"\""
  private val adbCommandClearElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:s:\"\""
  private val adbCommandClearElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:s:\"\""
  private val adbCommandClearAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:s:\"\""
  private val adbCommandClearHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:s:\"\""
  private val adbCommandClearSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:s:\"\""
  private val adbCommandClearPace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:s:\"\""
  private val adbCommandClearStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:s:\"\""
  private val adbCommandDeleteEntries = "content delete --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config"
  private val adbCommandSetMultipleCapabilities = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:false --bind CALORIES:b:false --bind DISTANCE:b:false --bind ELEVATION_GAIN:b:true --bind ELEVATION_LOSS:b:false --bind FLOORS:b:false --bind HEART_RATE_BPM:b:true --bind LOCATION:b:true --bind PACE:b:true --bind SPEED:b:false --bind STEPS:b:true --bind STEPS_PER_MINUTE:b:false"
  private val adbCommandMultipleFloatOverrides = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind CALORIES:f:123.0 --bind DISTANCE:f:12.0 --bind FLOORS:f:5.0"
  private val adbCommandFloatIntOverrides = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:f:5.0 --bind STEPS:i:55"
  private val adbCommandFloatIntNullOverrides = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:f:5.0 --bind PACE:s:\"\" --bind STEPS:i:55"
  private val adbCommandCheckWhsVersionCode = "dumpsys package com.google.android.wearable.healthservices | grep versionCode | head -n1"
  private val adbCommandQueryContentProvider = "content query --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config"

  private lateinit var adbSession: FakeAdbSession
  private val serialNumber: String = "1234"

  @Before
  fun setUp() {
    adbSession = FakeAdbSession()
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
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.setCapabilities(mapOf(dataType to true)) }, expectedAdbCommand)
  }

  private fun assertDisablingCapabilitySendsAdbCommand(dataType: WhsDataType, expectedAdbCommand: String) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.setCapabilities(mapOf(dataType to false)) }, expectedAdbCommand)
  }

  @Test
  fun `enable and disable steps`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.STEPS, adbCommandEnableSteps)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.STEPS, adbCommandDisableSteps)
  }

  @Test
  fun `enable and disable distance`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.DISTANCE, adbCommandEnableDistance)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.DISTANCE, adbCommandDisableDistance)
  }

  @Test
  fun `enable and disable total calories`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.CALORIES, adbCommandEnableTotalCalories)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.CALORIES, adbCommandDisableTotalCalories)
  }

  @Test
  fun `enable and disable floors`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.FLOORS, adbCommandEnableFloors)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.FLOORS, adbCommandDisableFloors)
  }

  @Test
  fun `enable and disable elevation gain`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_GAIN, adbCommandEnableElevationGain)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_GAIN, adbCommandDisableElevationGain)
  }

  @Test
  fun `enable and disable elevation loss`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_LOSS, adbCommandEnableElevationLoss)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_LOSS, adbCommandDisableElevationLoss)
  }

  @Test
  fun `enable and disable absolute elevation`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, adbCommandEnableAbsoluteElevation)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, adbCommandDisableAbsoluteElevation)
  }

  @Test
  fun `enable and disable location`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.LOCATION, adbCommandEnableLocation)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.LOCATION, adbCommandDisableLocation)
  }

  @Test
  fun `enable and disable heart rate bpm`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.HEART_RATE_BPM, adbCommandEnableHeartRateBpm)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.HEART_RATE_BPM, adbCommandDisableHeartRateBpm)
  }

  @Test
  fun `enable and disable speed`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.SPEED, adbCommandEnableSpeed)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.SPEED, adbCommandDisableSpeed)
  }

  @Test
  fun `enable and disable pace`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.PACE, adbCommandEnablePace)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.PACE, adbCommandDisablePace)
  }

  @Test
  fun `enable and disable steps per minute`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, adbCommandEnableStepsPerMinute)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, adbCommandDisableStepsPerMinute)
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
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValues(mapOf(dataType to overrideValue)) }, expectedAdbCommand)
  }

  @Test
  fun `override steps`() {
    assertOverrideSendsAdbCommand(WhsDataType.STEPS, 55, adbCommandSetStepsTo55)
    assertOverrideSendsAdbCommand(WhsDataType.STEPS, null, adbCommandClearSteps)
  }

  @Test
  fun `override distance`() {
    assertOverrideSendsAdbCommand(WhsDataType.DISTANCE, 10, adbCommandSetDistanceTo10)
    assertOverrideSendsAdbCommand(WhsDataType.DISTANCE, null, adbCommandClearDistance)
  }

  @Test
  fun `override total calories`() {
    assertOverrideSendsAdbCommand(WhsDataType.CALORIES, 100, adbCommandSetTotalCaloriesTo100)
    assertOverrideSendsAdbCommand(WhsDataType.CALORIES, null, adbCommandClearTotalCalories)
  }

  @Test
  fun `override floors`() {
    assertOverrideSendsAdbCommand(WhsDataType.FLOORS, 5, adbCommandSetFloorsTo5)
    assertOverrideSendsAdbCommand(WhsDataType.FLOORS, null, adbCommandClearFloors)
  }

  @Test
  fun `override elevation gain`() {
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_GAIN, 50, adbCommandSetElevationGainTo50)
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_GAIN, null, adbCommandClearElevationGain)
  }

  @Test
  fun `override elevation loss`() {
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_LOSS, 20, adbCommandSetElevationLossTo20)
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_LOSS, null, adbCommandClearElevationLoss)
  }

  @Test
  fun `override absolute elevation`() {
    assertOverrideSendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, 120, adbCommandSetAbsoluteElevationTo120)
    assertOverrideSendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, null, adbCommandClearAbsoluteElevation)
  }

  @Test
  fun `override heart rate bpm`() {
    assertOverrideSendsAdbCommand(WhsDataType.HEART_RATE_BPM, 65, adbCommandSetHeartRateBpmTo65)
    assertOverrideSendsAdbCommand(WhsDataType.HEART_RATE_BPM, null, adbCommandClearHeartRateBpm)
  }

  @Test
  fun `override speed`() {
    assertOverrideSendsAdbCommand(WhsDataType.SPEED, 30, adbCommandSetSpeedTo30)
    assertOverrideSendsAdbCommand(WhsDataType.SPEED, null, adbCommandClearSpeed)
  }

  @Test
  fun `override pace`() {
    assertOverrideSendsAdbCommand(WhsDataType.PACE, 20, adbCommandSetPaceTo20)
    assertOverrideSendsAdbCommand(WhsDataType.PACE, null, adbCommandClearPace)
  }

  @Test
  fun `override steps per minute`() {
    assertOverrideSendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, 25, adbCommandSetStepsPerMinuteTo25)
    assertOverrideSendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, null, adbCommandClearStepsPerMinute)
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
  fun `clear content provider without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.clearContentProvider()
    }
    job.join()
  }

  @Test
  fun `clear content provider triggers correct adb command`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.clearContentProvider() }, adbCommandDeleteEntries)
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
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.setCapabilities(mapOf(
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
    )) }, adbCommandSetMultipleCapabilities)
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
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValues(mapOf(
      WhsDataType.DISTANCE to 12.0,
      WhsDataType.CALORIES to 123.0,
      WhsDataType.FLOORS to 5.0,
    )) }, adbCommandMultipleFloatOverrides)
  }

  @Test
  fun `setting float and int override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValues(mapOf(
      WhsDataType.STEPS to 55,
      WhsDataType.ELEVATION_LOSS to 5.0,
    )) }, adbCommandFloatIntOverrides)
  }

  @Test
  fun `setting float, int and null override values triggers expected adb command with keys in alphabetical order`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValues(mapOf(
      WhsDataType.STEPS to 55,
      WhsDataType.ELEVATION_LOSS to 5.0,
      WhsDataType.PACE to null,
    )) }, adbCommandFloatIntNullOverrides)
  }

  @Test
  fun `setting location override value is ignored`() {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValues(mapOf(
      WhsDataType.STEPS to 55,
      WhsDataType.ELEVATION_LOSS to 5.0,
      WhsDataType.PACE to null,
      WhsDataType.LOCATION to null,
    )) }, adbCommandFloatIntNullOverrides)
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
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), adbCommandCheckWhsVersionCode, response)

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

    assertEquals(adbCommandCheckWhsVersionCode, shellRequest.command)
    assertEquals(expectedIsSupportedBool, isSupported)
  }

  private fun assertExerciseCommandParsesResultsCorrectly(response: String, expected: Boolean) = runTest {
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), adbCommandActiveExercise,
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
    assertEquals(adbCommandActiveExercise, shellRequest.command)
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
  fun `unexpected ADB response results in WHS version being reported as unsupported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("Unexpected response", false)
  }

  @Test
  fun `dev WHS version codes are supported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("    versionCode=1 minSdk=30 targetSdk=33", true)
  }

  @Test
  fun `non dev WHS version codes are not supported`() {
    assertWhsVersionCheckAdbResponseIsParsedCorrectly("    versionCode=1417661 minSdk=30 targetSdk=33", false)
  }

  @Test
  fun `loading capabilities without setting serial number does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.loadCapabilities()
    }
    job.join()
  }

  private fun assertLoadCapabilitiesAdbResponseIsParsedCorrectly(response: String, expectedCapabilites: Map<WhsDataType, CapabilityStatus>) = runTest {
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), adbCommandQueryContentProvider, response)

    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    var parsedCapabilities = WHS_CAPABILITIES.associate { it.dataType to CapabilityStatus(false, null) }
    val job = launch {
      parsedCapabilities = deviceManager.loadCurrentCapabilityStatus()
    }
    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(adbCommandQueryContentProvider, shellRequest.command)

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
                                                        WhsDataType.STEPS_PER_MINUTE to CapabilityStatus(false, null),
                                                        WhsDataType.SPEED to CapabilityStatus(true, null),
                                                        WhsDataType.FLOORS to CapabilityStatus(false, null),
                                                        WhsDataType.ABSOLUTE_ELEVATION to CapabilityStatus(false, null),
                                                        WhsDataType.ELEVATION_LOSS to CapabilityStatus(false, null),
                                                        WhsDataType.DISTANCE to CapabilityStatus(true, null),
                                                        WhsDataType.ELEVATION_GAIN to CapabilityStatus(false, null),
                                                        WhsDataType.CALORIES to CapabilityStatus(false, null),
                                                        WhsDataType.PACE to CapabilityStatus(false, null),
                                                        WhsDataType.HEART_RATE_BPM to CapabilityStatus(true, null),
                                                        WhsDataType.STEPS to CapabilityStatus(true, null),
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
                                                        WhsDataType.STEPS to CapabilityStatus(true, null),
                                                      ))
  }
}
