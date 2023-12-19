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
  private var adbCommandEnableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:true --where \"STEPS\""
  private var adbCommandEnableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:true --where \"DISTANCE\""
  private var adbCommandEnableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:b:true --where \"TOTAL_CALORIES\""
  private var adbCommandEnableFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:b:true --where \"FLOORS\""
  private var adbCommandEnableElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:b:true --where \"ELEVATION_GAIN\""
  private var adbCommandEnableElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:b:true --where \"ELEVATION_LOSS\""
  private var adbCommandEnableAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:true --where \"ABSOLUTE_ELEVATION\""
  private var adbCommandEnableLocation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind LOCATION:b:true --where \"LOCATION\""
  private var adbCommandEnableHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:b:true --where \"HEART_RATE_BPM\""
  private var adbCommandEnableSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:b:true --where \"SPEED\""
  private var adbCommandEnablePace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:b:true --where \"PACE\""
  private var adbCommandEnableStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:b:true --where \"STEPS_PER_MINUTE\""
  private var adbCommandDisableSteps = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS:b:false --where \"STEPS\""
  private var adbCommandDisableDistance = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind DISTANCE:b:false --where \"DISTANCE\""
  private var adbCommandDisableTotalCalories = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind TOTAL_CALORIES:b:false --where \"TOTAL_CALORIES\""
  private var adbCommandDisableFloors = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind FLOORS:b:false --where \"FLOORS\""
  private var adbCommandDisableElevationGain = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_GAIN:b:false --where \"ELEVATION_GAIN\""
  private var adbCommandDisableElevationLoss = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ELEVATION_LOSS:b:false --where \"ELEVATION_LOSS\""
  private var adbCommandDisableAbsoluteElevation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind ABSOLUTE_ELEVATION:b:false --where \"ABSOLUTE_ELEVATION\""
  private var adbCommandDisableLocation = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind LOCATION:b:false --where \"LOCATION\""
  private var adbCommandDisableHeartRateBpm = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind HEART_RATE_BPM:b:false --where \"HEART_RATE_BPM\""
  private var adbCommandDisableSpeed = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind SPEED:b:false --where \"SPEED\""
  private var adbCommandDisablePace = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind PACE:b:false --where \"PACE\""
  private var adbCommandDisableStepsPerMinute = "content update --uri content://com.google.android.wearable.healthservices.dev.synthetic/synthetic_config --bind STEPS_PER_MINUTE:b:false --where \"STEPS_PER_MINUTE\""

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

  private fun `Changing capability sends expected adb command`(capability: WhsCapability, newValue: Boolean, expectedAdbCommand: String) = runTest {
    adbSession.deviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(serialNumber), expectedAdbCommand,"")

    val deviceManager = ContentProviderDeviceManager(adbSession)
    deviceManager.setSerialNumber(serialNumber)

    val job = launch {
      if (newValue) {
        deviceManager.enableCapability(capability)
      } else {
        deviceManager.disableCapability(capability)
      }
    }
    job.join()

    assertEquals(1, adbSession.deviceServices.shellV2Requests.size)

    val shellRequest = adbSession.deviceServices.shellV2Requests.last

    assert(shellRequest.deviceSelector.contains(serialNumber))
    assertEquals(expectedAdbCommand, shellRequest.command)
  }

  @Test
  fun `Enable steps`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.STEPS,
        "wear.whs.capability.steps.label",
        "wear.whs.capability.steps.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableSteps
    )
  }

  @Test
  fun `Disable steps`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.STEPS,
        "wear.whs.capability.steps.label",
        "wear.whs.capability.steps.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableSteps
    )
  }

  @Test
  fun `Enable distance`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.DISTANCE,
        "wear.whs.capability.distance.label",
        "wear.whs.capability.distance.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableDistance
    )
  }

  @Test
  fun `Disable distance`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.DISTANCE,
        "wear.whs.capability.distance.label",
        "wear.whs.capability.distance.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableDistance
    )
  }

  @Test
  fun `Enable total calories`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.TOTAL_CALORIES,
        "wear.whs.capability.total.calories.label",
        "wear.whs.capability.total.calories.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableTotalCalories
    )
  }

  @Test
  fun `Disable total calories`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.TOTAL_CALORIES,
        "wear.whs.capability.total.calories.label",
        "wear.whs.capability.total.calories.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableTotalCalories
    )
  }

  @Test
  fun `Enable floors`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.FLOORS,
        "wear.whs.capability.floors.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableFloors
    )
  }

  @Test
  fun `Disable floors`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.FLOORS,
        "wear.whs.capability.floors.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableFloors
    )
  }

  @Test
  fun `Enable elevation gain`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.ELEVATION_GAIN,
        "wear.whs.capability.elevation.gain.label",
        "wear.whs.capability.elevation.gain.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableElevationGain
    )
  }

  @Test
  fun `Disable elevation gain`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.ELEVATION_GAIN,
        "wear.whs.capability.elevation.gain.label",
        "wear.whs.capability.elevation.gain.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableElevationGain
    )
  }

  @Test
  fun `Enable elevation loss`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.ELEVATION_LOSS,
        "wear.whs.capability.elevation.loss.label",
        "wear.whs.capability.elevation.loss.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableElevationLoss
    )
  }

  @Test
  fun `Disable elevation loss`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.ELEVATION_LOSS,
        "wear.whs.capability.elevation.loss.label",
        "wear.whs.capability.elevation.loss.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableElevationLoss
    )
  }

  @Test
  fun `Enable absolute elevation`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.ABSOLUTE_ELEVATION,
        "wear.whs.capability.absolute.elevation.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableAbsoluteElevation
    )
  }

  @Test
  fun `Disable absolute elevation`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.ABSOLUTE_ELEVATION,
        "wear.whs.capability.absolute.elevation.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableAbsoluteElevation
    )
  }


  @Test
  fun `Enable location`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.LOCATION,
        "wear.whs.capability.location.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableLocation
    )
  }

  @Test
  fun `Disable location`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.LOCATION,
        "wear.whs.capability.location.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableLocation
    )
  }

  @Test
  fun `Enable heart rate bpm`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.HEART_RATE_BPM,
        "wear.whs.capability.heart.rate.label",
        "wear.whs.capability.heart.rate.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableHeartRateBpm
    )
  }

  @Test
  fun `Disable heart rate bpm`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.HEART_RATE_BPM,
        "wear.whs.capability.heart.rate.label",
        "wear.whs.capability.heart.rate.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableHeartRateBpm
    )
  }

  @Test
  fun `Enable speed`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.SPEED,
        "wear.whs.capability.speed.label",
        "wear.whs.capability.speed.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableSpeed
    )
  }

  @Test
  fun `Disable speed`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.SPEED,
        "wear.whs.capability.speed.label",
        "wear.whs.capability.speed.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableSpeed
    )
  }

  @Test
  fun `Enable pace`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.PACE,
        "wear.whs.capability.pace.label",
        "wear.whs.capability.pace.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnablePace
    )
  }

  @Test
  fun `Disable pace`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.PACE,
        "wear.whs.capability.pace.label",
        "wear.whs.capability.pace.unit",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisablePace
    )
  }

  @Test
  fun `Enable steps per minute`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.STEPS_PER_MINUTE,
        "wear.whs.capability.steps.per.minute.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), true, adbCommandEnableStepsPerMinute
    )
  }

  @Test
  fun `Disable steps per minute`() {
    `Changing capability sends expected adb command`(
      WhsCapability(
        WhsDataType.STEPS_PER_MINUTE,
        "wear.whs.capability.steps.per.minute.label",
        "wear.whs.capability.unit.none",
        isOverrideable = true,
        isStandardCapability = true,
      ), false, adbCommandDisableStepsPerMinute
    )
  }
}