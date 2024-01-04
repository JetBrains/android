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
  private var adbCommandEnableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:true"
  private var adbCommandEnableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:true"
  private var adbCommandEnableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:b:true"
  private var adbCommandEnableFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:b:true"
  private var adbCommandEnableElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:b:true"
  private var adbCommandEnableElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:b:true"
  private var adbCommandEnableAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:true"
  private var adbCommandEnableLocation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind LOCATION:b:true"
  private var adbCommandEnableHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:b:true"
  private var adbCommandEnableSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:b:true"
  private var adbCommandEnablePace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:b:true"
  private var adbCommandEnableStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:b:true"
  private var adbCommandDisableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:false"
  private var adbCommandDisableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:false"
  private var adbCommandDisableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:b:false"
  private var adbCommandDisableFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:b:false"
  private var adbCommandDisableElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:b:false"
  private var adbCommandDisableElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:b:false"
  private var adbCommandDisableAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:false"
  private var adbCommandDisableLocation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind LOCATION:b:false"
  private var adbCommandDisableHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:b:false"
  private var adbCommandDisableSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:b:false"
  private var adbCommandDisablePace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:b:false"
  private var adbCommandDisableStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:b:false"

  private lateinit var adbSession: FakeAdbSession
  private val serialNumber: String = "1234"

  @Before
  fun setUp() {
    adbSession = FakeAdbSession()
  }

  @Test
  fun `Enabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.enableCapability(WhsCapability(
        WhsDataType.STEPS,
        "wear.whs.capability.steps.label",
        "wear.whs.capability.steps.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ))
    }
    job.join()
  }

  @Test
  fun `Disabling capability when serial number is not set does not result in crash`() = runTest {
    val deviceManager = ContentProviderDeviceManager(adbSession)

    val job = launch {
      deviceManager.disableCapability(WhsCapability(
        WhsDataType.STEPS,
        "wear.whs.capability.steps.label",
        "wear.whs.capability.steps.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ))
    }
    job.join()
  }

  private fun assertCapabilitySendsAdbCommand(capability: WhsCapability, newValue: Boolean, expectedAdbCommand: String) = runTest {
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), expectedAdbCommand,"")

    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val previousCount = adbSession.deviceServices.shellV2Requests.size

    val job = launch {
      if (newValue) {
        deviceManager.enableCapability(capability)
      } else {
        deviceManager.disableCapability(capability)
      }
    }
    job.join()

    val currentCount = adbSession.deviceServices.shellV2Requests.size
    val newRequestsCount = currentCount - previousCount

    assertEquals(1, newRequestsCount)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(expectedAdbCommand, shellRequest.command)
  }

  @Test
  fun `Enable and disable steps`() {
    val capability = WhsCapability(
      WhsDataType.STEPS,
      "wear.whs.capability.steps.label",
      "wear.whs.capability.steps.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableSteps)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableSteps)
  }

  @Test
  fun `Enable and disable distance`() {
    val capability = WhsCapability(
      WhsDataType.DISTANCE,
      "wear.whs.capability.distance.label",
      "wear.whs.capability.distance.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableDistance)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableDistance)
  }

  @Test
  fun `Enable and disable total calories`() {
    val capability = WhsCapability(
      WhsDataType.TOTAL_CALORIES,
      "wear.whs.capability.total.calories.label",
      "wear.whs.capability.total.calories.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableTotalCalories)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableTotalCalories)
  }

  @Test
  fun `Enable and disable floors`() {
    val capability = WhsCapability(
      WhsDataType.FLOORS,
      "wear.whs.capability.floors.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableFloors)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableFloors)
  }

  @Test
  fun `Enable and disable elevation gain`() {
    val capability = WhsCapability(
      WhsDataType.ELEVATION_GAIN,
      "wear.whs.capability.elevation.gain.label",
      "wear.whs.capability.elevation.gain.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableElevationGain)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableElevationGain)
  }

  @Test
  fun `Enable and disable elevation loss`() {
    val capability = WhsCapability(
      WhsDataType.ELEVATION_LOSS,
      "wear.whs.capability.elevation.loss.label",
      "wear.whs.capability.elevation.loss.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableElevationLoss)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableElevationLoss)
  }

  @Test
  fun `Enable and disable absolute elevation`() {
    val capability = WhsCapability(
      WhsDataType.ABSOLUTE_ELEVATION,
      "wear.whs.capability.absolute.elevation.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableAbsoluteElevation)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableAbsoluteElevation)
  }

  @Test
  fun `Enable and disable location`() {
    val capability = WhsCapability(
      WhsDataType.LOCATION,
      "wear.whs.capability.location.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableLocation)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableLocation)
  }

  @Test
  fun `Enable and disable heart rate bpm`() {
    val capability = WhsCapability(
      WhsDataType.HEART_RATE_BPM,
      "wear.whs.capability.heart.rate.label",
      "wear.whs.capability.heart.rate.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableHeartRateBpm)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableHeartRateBpm)
  }

  @Test
  fun `Enable and disable speed`() {
    val capability = WhsCapability(
      WhsDataType.SPEED,
      "wear.whs.capability.speed.label",
      "wear.whs.capability.speed.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableSpeed)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableSpeed)
  }

  @Test
  fun `Enable and disable pace`() {
    val capability = WhsCapability(
      WhsDataType.PACE,
      "wear.whs.capability.pace.label",
      "wear.whs.capability.pace.unit",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnablePace)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisablePace)
  }

  @Test
  fun `Enable and disable steps per minute`() {
    val capability = WhsCapability(
      WhsDataType.STEPS_PER_MINUTE,
      "wear.whs.capability.steps.per.minute.label",
      "wear.whs.capability.unit.none",
      isOverrideable = true,
      isStandardCapability = true,
    )

    assertCapabilitySendsAdbCommand(capability, true, adbCommandEnableStepsPerMinute)
    assertCapabilitySendsAdbCommand(capability, false, adbCommandDisableStepsPerMinute)
  }
}