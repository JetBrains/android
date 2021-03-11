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
package com.android.tools.idea.run.deployment

import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons.General.WarningDecorator
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import icons.StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
import icons.StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV
import icons.StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import javax.swing.Icon

@RunWith(JUnit4::class)
class PhysicalDeviceTest {

  private fun assertIconSimilar(expectedIcon: Icon, actualIcon: Icon) {
    val expectedIconImage = ImageUtil.toBufferedImage(IconUtil.toImage(expectedIcon, ScaleContext.createIdentity()))
    val actualIconImage = ImageUtil.toBufferedImage(IconUtil.toImage(actualIcon, ScaleContext.createIdentity()))
    assertImageSimilar("icon", expectedIconImage, actualIconImage, 0.0)
  }

  @Before
  fun activateIconLoader() {
    IconManager.activate()
    IconLoader.activate()
  }

  @After
  fun deactivateIconLoader() {
    IconManager.deactivate()
    IconLoader.deactivate()
  }

  @Test
  fun testGetPhoneWithoutErrorOrWarningIcon() {
    val phoneWithoutErrorOrWarning = PhysicalDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(SerialNumber("86UX00F4R"))
      .setType(Device.Type.PHONE)
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .build()

    assertIconSimilar(ExecutionUtil.getLiveIndicator(PHYSICAL_DEVICE_PHONE), phoneWithoutErrorOrWarning.icon)
  }

  @Test
  fun testGetWearWithErrorIcon() {
    val wearWithError = PhysicalDevice.Builder()
      .setName("Wear API 30")
      .setKey(SerialNumber("86UX00F4R"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setType(Device.Type.WEAR)
      .setLaunchCompatibility(LaunchCompatibility(LaunchCompatibility.State.ERROR, "error"))
      .build()

    //TODO(b/180670146): replace with error decorator.
    assertIconSimilar(LayeredIcon(ExecutionUtil.getLiveIndicator(PHYSICAL_DEVICE_WEAR), WarningDecorator), wearWithError.icon)
  }

  @Test
  fun testGetTvWithWarningIcon() {
    val tvWithWarning = PhysicalDevice.Builder()
      .setName("TV API 30")
      .setKey(SerialNumber("86UX00F4R"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setType(Device.Type.TV)
      .setLaunchCompatibility(LaunchCompatibility(LaunchCompatibility.State.WARNING, "warning"))
      .build()

    assertIconSimilar(LayeredIcon(ExecutionUtil.getLiveIndicator(PHYSICAL_DEVICE_TV), WarningDecorator), tvWithWarning.icon)
  }
}
