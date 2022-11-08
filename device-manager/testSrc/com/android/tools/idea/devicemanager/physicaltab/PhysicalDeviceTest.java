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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.device.Resolution;
import com.android.tools.idea.devicemanager.SerialNumber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTest {
  @Test
  public void getDpDensityEqualsNegativeOne() {
    // Act
    Object dp = TestPhysicalDevices.GOOGLE_PIXEL_3.getDp();

    // Assert
    assertNull(dp);
  }

  @Test
  public void getDpResolutionIsNull() {
    // Arrange
    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setAndroidVersion(new AndroidVersion(31))
      .setDensity(440)
      .build();

    // Act
    Object dp = device.getDp();

    // Assert
    assertNull(dp);
  }

  @Test
  public void getDp() {
    // Arrange
    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setAndroidVersion(new AndroidVersion(31))
      .setResolution(new Resolution(1080, 2160))
      .setDensity(440)
      .build();

    // Act
    Object dp = device.getDp();

    // Assert
    assertEquals(new Resolution(393, 786), dp);
  }
}
