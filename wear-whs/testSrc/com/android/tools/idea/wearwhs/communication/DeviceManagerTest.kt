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
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagerTest {
  private val adbCommandEnableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:true"
  private val adbCommandEnableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:true"
  private val adbCommandEnableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:b:true"
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
  private val adbCommandDisableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:b:false"
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
  private val adbCommandSetTotalCaloriesTo100 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:f:100.0"
  private val adbCommandSetFloorsTo5 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:f:5.0"
  private val adbCommandSetElevationGainTo50 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:f:50.0"
  private val adbCommandSetElevationLossTo20 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:f:20.0"
  private val adbCommandSetAbsoluteElevationTo120 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:f:120.0"
  private val adbCommandSetHeartRateBpmTo65 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:f:65.0"
  private val adbCommandSetSpeedTo30 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:f:30.0"
  private val adbCommandSetPaceTo20 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:f:20.0"
  private val adbCommandSetStepsPerMinuteTo25 = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:f:25.0"

  private val capabilities = mapOf(
    WhsDataType.STEPS to WhsCapability(
      WhsDataType.STEPS,
      "wear.whs.capability.steps.label",
      "wear.whs.capability.steps.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.DISTANCE to WhsCapability(
      WhsDataType.DISTANCE,
      "wear.whs.capability.distance.label",
      "wear.whs.capability.distance.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.TOTAL_CALORIES to WhsCapability(
      WhsDataType.TOTAL_CALORIES,
      "wear.whs.capability.total.calories.label",
      "wear.whs.capability.total.calories.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.FLOORS to WhsCapability(
      WhsDataType.FLOORS,
      "wear.whs.capability.floors.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.ELEVATION_GAIN to WhsCapability(
      WhsDataType.ELEVATION_GAIN,
      "wear.whs.capability.elevation.gain.label",
      "wear.whs.capability.elevation.gain.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.ELEVATION_LOSS to WhsCapability(
      WhsDataType.ELEVATION_LOSS,
      "wear.whs.capability.elevation.loss.label",
      "wear.whs.capability.elevation.loss.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.ABSOLUTE_ELEVATION to WhsCapability(
      WhsDataType.ABSOLUTE_ELEVATION,
      "wear.whs.capability.absolute.elevation.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.LOCATION to WhsCapability(
      WhsDataType.LOCATION,
      "wear.whs.capability.location.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.HEART_RATE_BPM to WhsCapability(
      WhsDataType.HEART_RATE_BPM,
      "wear.whs.capability.heart.rate.label",
      "wear.whs.capability.heart.rate.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.SPEED to WhsCapability(
      WhsDataType.SPEED,
      "wear.whs.capability.speed.label",
      "wear.whs.capability.speed.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.PACE to WhsCapability(
      WhsDataType.PACE,
      "wear.whs.capability.pace.label",
      "wear.whs.capability.pace.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsDataType.STEPS_PER_MINUTE to WhsCapability(
      WhsDataType.STEPS_PER_MINUTE,
      "wear.whs.capability.steps.per.minute.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    ),
  )

  private lateinit var adbSession: FakeAdbSession
  private val serialNumber: String = "1234"

  @Before
  fun setUp() {
    adbSession = FakeAdbSession()
  }

  private fun WhsDataType.toCapability(): WhsCapability = capabilities[this]!!

  @Test
  fun `Enabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.enableCapability(WhsDataType.STEPS.toCapability())
    }
    job.join()
  }

  @Test
  fun `Disabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.disableCapability(WhsDataType.STEPS.toCapability())
    }
    job.join()
  }

  private fun assertDeviceManagerFunctionSendsAdbCommand(func: suspend (WearHealthServicesDeviceManager) -> Unit, expectedAdbCommand: String) = runTest {
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), expectedAdbCommand,"")

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
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.enableCapability(dataType.toCapability()) }, expectedAdbCommand)
  }

  private fun assertDisablingCapabilitySendsAdbCommand(dataType: WhsDataType, expectedAdbCommand: String) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.disableCapability(dataType.toCapability()) }, expectedAdbCommand)
  }

  @Test
  fun `Enable and disable steps`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.STEPS, adbCommandEnableSteps)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.STEPS, adbCommandDisableSteps)
  }

  @Test
  fun `Enable and disable distance`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.DISTANCE, adbCommandEnableDistance)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.DISTANCE, adbCommandDisableDistance)
  }

  @Test
  fun `Enable and disable total calories`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.TOTAL_CALORIES, adbCommandEnableTotalCalories)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.TOTAL_CALORIES, adbCommandDisableTotalCalories)
  }

  @Test
  fun `Enable and disable floors`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.FLOORS, adbCommandEnableFloors)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.FLOORS, adbCommandDisableFloors)
  }

  @Test
  fun `Enable and disable elevation gain`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_GAIN, adbCommandEnableElevationGain)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_GAIN, adbCommandDisableElevationGain)
  }

  @Test
  fun `Enable and disable elevation loss`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_LOSS, adbCommandEnableElevationLoss)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ELEVATION_LOSS, adbCommandDisableElevationLoss)
  }

  @Test
  fun `Enable and disable absolute elevation`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, adbCommandEnableAbsoluteElevation)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, adbCommandDisableAbsoluteElevation)
  }

  @Test
  fun `Enable and disable location`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.LOCATION, adbCommandEnableLocation)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.LOCATION, adbCommandDisableLocation)
  }

  @Test
  fun `Enable and disable heart rate bpm`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.HEART_RATE_BPM, adbCommandEnableHeartRateBpm)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.HEART_RATE_BPM, adbCommandDisableHeartRateBpm)
  }

  @Test
  fun `Enable and disable speed`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.SPEED, adbCommandEnableSpeed)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.SPEED, adbCommandDisableSpeed)
  }

  @Test
  fun `Enable and disable pace`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.PACE, adbCommandEnablePace)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.PACE, adbCommandDisablePace)
  }

  @Test
  fun `Enable and disable steps per minute`() {
    assertEnablingCapabilitySendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, adbCommandEnableStepsPerMinute)
    assertDisablingCapabilitySendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, adbCommandDisableStepsPerMinute)
  }

  @Test
  fun `Setting capability override value when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.overrideValue(WhsDataType.STEPS.toCapability(), 55)
    }
    job.join()
  }

  private fun assertOverrideSendsAdbCommand(dataType: WhsDataType, overrideValue: Number?, expectedAdbCommand: String) = runTest {
    assertDeviceManagerFunctionSendsAdbCommand({ deviceManager -> deviceManager.overrideValue(dataType.toCapability(), overrideValue) }, expectedAdbCommand)
  }

  @Test
  fun `Override steps`() = runTest {
    assertOverrideSendsAdbCommand(WhsDataType.STEPS, 55, adbCommandSetStepsTo55)
  }

  @Test
  fun `Override distance`() {
    assertOverrideSendsAdbCommand(WhsDataType.DISTANCE, 10, adbCommandSetDistanceTo10)
  }

  @Test
  fun `Override total calories`() {
    assertOverrideSendsAdbCommand(WhsDataType.TOTAL_CALORIES, 100, adbCommandSetTotalCaloriesTo100)
  }

  @Test
  fun `Override floors`() {
    assertOverrideSendsAdbCommand(WhsDataType.FLOORS, 5, adbCommandSetFloorsTo5)
  }

  @Test
  fun `Override elevation gain`() {
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_GAIN, 50, adbCommandSetElevationGainTo50)
  }

  @Test
  fun `Override elevation loss`() {
    assertOverrideSendsAdbCommand(WhsDataType.ELEVATION_LOSS, 20, adbCommandSetElevationLossTo20)
  }

  @Test
  fun `Override absolute elevation`() {
    assertOverrideSendsAdbCommand(WhsDataType.ABSOLUTE_ELEVATION, 120, adbCommandSetAbsoluteElevationTo120)
  }

  @Test
  fun `Override heart rate bpm`() {
    assertOverrideSendsAdbCommand(WhsDataType.HEART_RATE_BPM, 65, adbCommandSetHeartRateBpmTo65)
  }

  @Test
  fun `Override speed`() {
    assertOverrideSendsAdbCommand(WhsDataType.SPEED, 30, adbCommandSetSpeedTo30)
  }

  @Test
  fun `Override pace`() {
    assertOverrideSendsAdbCommand(WhsDataType.PACE, 20, adbCommandSetPaceTo20)
  }

  @Test
  fun `Override steps per minute`() {
    assertOverrideSendsAdbCommand(WhsDataType.STEPS_PER_MINUTE, 25, adbCommandSetStepsPerMinuteTo25)
  }
}