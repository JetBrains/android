/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector

import com.android.testutils.MockitoKt
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibility.State
import com.intellij.icons.AllIcons
import com.intellij.ui.LayeredIcon
import icons.StudioIcons
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.function.UnaryOperator
import javax.swing.Icon

@RunWith(JUnit4::class)
class PhysicalDeviceTest {
  private val getLiveIndicator = MockitoKt.mock<UnaryOperator<Icon>>()
  private val runningIcon = MockitoKt.mock<Icon>()

  @Test
  fun testGetPhoneWithoutErrorOrWarningIcon() {
    MockitoKt.whenever(getLiveIndicator.apply(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)).thenReturn(runningIcon)

    val phoneWithoutErrorOrWarning = PhysicalDevice.Builder()
      .setKey(SerialNumber("86UX00F4R"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("Pixel 4 API 30")
      .setAndroidDevice(MockitoKt.mock<AndroidDevice>())
      .setGetLiveIndicator(getLiveIndicator)
      .build()

    assertEquals(runningIcon, phoneWithoutErrorOrWarning.icon())
  }

  @Test
  fun testGetWearWithErrorIcon() {
    MockitoKt.whenever(getLiveIndicator.apply(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR)).thenReturn(runningIcon)

    val wearWithError = PhysicalDevice.Builder()
      .setKey(SerialNumber("86UX00F4R"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR)
      .setLaunchCompatibility(LaunchCompatibility(State.ERROR, "error"))
      .setName("Wear API 30")
      .setAndroidDevice(MockitoKt.mock<AndroidDevice>())
      .setGetLiveIndicator(getLiveIndicator)
      .build()

    assertEquals(LayeredIcon(runningIcon, StudioIcons.Common.ERROR_DECORATOR), wearWithError.icon())
  }

  @Test
  fun testGetTvWithWarningIcon() {
    MockitoKt.whenever(getLiveIndicator.apply(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV)).thenReturn(runningIcon)

    val tvWithWarning = PhysicalDevice.Builder()
      .setKey(SerialNumber("86UX00F4R"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV)
      .setLaunchCompatibility(LaunchCompatibility(State.WARNING, "warning"))
      .setName("TV API 30")
      .setAndroidDevice(MockitoKt.mock<AndroidDevice>())
      .setGetLiveIndicator(getLiveIndicator)
      .build()

    assertEquals(LayeredIcon(runningIcon, AllIcons.General.WarningDecorator), tvWithWarning.icon())
  }
}
