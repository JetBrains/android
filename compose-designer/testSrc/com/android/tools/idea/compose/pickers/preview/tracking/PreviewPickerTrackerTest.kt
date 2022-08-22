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
package com.android.tools.idea.compose.pickers.preview.tracking

import com.android.ide.common.rendering.api.HardwareConfig
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.configurations.Configuration
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.DeviceType
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerParameter
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import kotlin.math.sqrt
import kotlin.test.assertEquals
import org.junit.Test

internal class PreviewPickerTrackerTest {

  @Test
  fun doLogUsageCalledWhenIntended() {
    val tracker = TestTracker()
    tracker.logUsageData()

    // Need to show and close picker to submit data
    assertEquals(0, tracker.doLogUsageCallCount)

    tracker.pickerShown()
    tracker.logUsageData()

    // Need to close picker to submit data
    assertEquals(0, tracker.doLogUsageCallCount)

    tracker.pickerClosed()
    tracker.logUsageData()

    // Picker was shown and closed, may now submit data
    assertEquals(1, tracker.doLogUsageCallCount)

    // Trying to log again, will not call 'doLogUsageData'
    tracker.logUsageData()

    assertEquals(1, tracker.doLogUsageCallCount)
  }

  @Test
  fun testDeviceTracked() {
    val tracker =
      TestTracker().applyValidModifications {
        registerNameModificationWithDevice(
          isCustom = true,
          isGeneric = false,
          tagId = SystemImage.ANDROID_TV_TAG.id
        )
        registerNameModificationWithDevice(
          isCustom = false,
          isGeneric = false,
          tagId = SystemImage.ANDROID_TV_TAG.id
        )
        registerNameModificationWithDevice(
          isCustom = false,
          isGeneric = false,
          tagId = SystemImage.AUTOMOTIVE_TAG.id
        )
        registerNameModificationWithDevice(
          isCustom = false,
          isGeneric = false,
          tagId = SystemImage.WEAR_TAG.id
        )
        registerNameModificationWithDevice(
          isCustom = false,
          isGeneric = false,
          tagId = SystemImage.DEFAULT_TAG.id
        )
        registerNameModificationWithDevice(
          isCustom = false,
          isGeneric = true,
          tagId = SystemImage.DEFAULT_TAG.id
        )
        registerNameModificationWithDevice(
          isCustom = false,
          isGeneric = false,
          tagId = SystemImage.DESKTOP_TAG.id
        )
      }

    val registeredActions = tracker.lastActionsLogged.toList()
    assertEquals(7, registeredActions.size)

    val registeredDeviceTypes = registeredActions.map { it.previewModification.closestDeviceType }
    assertEquals(DeviceType.CUSTOM, registeredDeviceTypes[0])
    assertEquals(DeviceType.TV, registeredDeviceTypes[1])
    assertEquals(DeviceType.UNKNOWN_DEVICE_TYPE, registeredDeviceTypes[2])
    assertEquals(DeviceType.WEAR, registeredDeviceTypes[3])
    assertEquals(DeviceType.PHONE, registeredDeviceTypes[4])
    assertEquals(DeviceType.GENERIC, registeredDeviceTypes[5])
    assertEquals(DeviceType.DESKTOP, registeredDeviceTypes[6])
  }

  @Test
  fun testParameterTracked() {
    val tracker =
      TestTracker().applyValidModifications {
        // Properties are case-sensitive
        registerModificationWithCustomDevice("name") // actual field in @Preview annotation
        registerModificationWithCustomDevice("Name") // invalid, fields are case-sensitive

        // Unknown parameter
        registerModificationWithCustomDevice("abcde") // non-existent field
      }

    val registeredActions = tracker.lastActionsLogged.toList()
    assertEquals(3, registeredActions.size)

    val registeredParameters = registeredActions.map { it.previewModification.parameter }
    assertEquals(PreviewPickerParameter.NAME, registeredParameters[0])
    assertEquals(PreviewPickerParameter.UNKNOWN_PREVIEW_PICKER_PARAMETER, registeredParameters[1])
    assertEquals(PreviewPickerParameter.UNKNOWN_PREVIEW_PICKER_PARAMETER, registeredParameters[2])
  }
}

private class TestTracker : PreviewPickerTracker() {
  var doLogUsageCallCount = 0
  val lastActionsLogged = mutableListOf<EditorPickerAction>()

  override fun doLogUsageData(actions: List<EditorPickerAction>) {
    doLogUsageCallCount++
    lastActionsLogged.clear()
    lastActionsLogged.addAll(actions)
  }
}

/**
 * Sets the tracker so that all the modifications done in [runnable] are present in
 * [TestTracker.lastActionsLogged].
 */
private fun TestTracker.applyValidModifications(
  runnable: TrackerModificationsWrapper.() -> Unit
): TestTracker {
  pickerShown()
  runnable(TrackerModificationsWrapper(this))
  pickerClosed()
  logUsageData()
  return this
}

/** A Class wrapper for [TestTracker] that only allows to register modifications. */
private class TrackerModificationsWrapper(private val tracker: TestTracker) {
  fun registerModification(name: String, value: PreviewPickerValue, device: Device?) =
    tracker.registerModification(name, value, device)

  fun registerNameModificationWithDevice(isCustom: Boolean, isGeneric: Boolean, tagId: String) =
    registerModification(
      "name",
      PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED,
      createDevice(isCustom, isGeneric, tagId)
    )

  fun registerModificationWithCustomDevice(parameterName: String) =
    registerModification(
      parameterName,
      PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED,
      createDevice(isCustom = true, isGeneric = false, tagId = "tagId")
    )
}

private fun createDevice(isCustom: Boolean, isGeneric: Boolean, tagId: String): Device {
  val customDevice =
    Device.Builder()
      .apply {
        setTagId("")
        setName("Custom")
        if (isCustom) {
          setId(Configuration.CUSTOM_DEVICE_ID)
        } else {
          setId("my_device")
          setTagId(tagId)
        }
        if (isGeneric) {
          setManufacturer(HardwareConfig.MANUFACTURER_GENERIC)
        } else {
          setManufacturer(HardwareConfig.MANUFACTURER_GOOGLE)
        }
        addSoftware(Software())
        addState(State().apply { isDefaultState = true })
      }
      .build()
  customDevice.defaultState.apply {
    orientation = ScreenOrientation.PORTRAIT
    hardware =
      Hardware().apply {
        screen =
          Screen().apply {
            xDimension = 1080
            yDimension = 1920
            pixelDensity = Density.XXHIGH
            diagonalLength =
              sqrt((1.0 * xDimension * xDimension) + (1.0 * yDimension * yDimension)) /
                pixelDensity.dpiValue
            screenRound = ScreenRound.NOTROUND
            chin = 0
            size = ScreenSize.getScreenSize(diagonalLength)
            ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)
          }
      }
  }
  return customDevice
}
