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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.DeviceType;
import icons.StudioIcons;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTest {
  @Test
  public void buildPhone() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Pixel_3_API_30"))
      .setType(DeviceType.PHONE)
      .setName("Google Pixel 3")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, device.getIcon());
  }

  @Test
  public void buildWearOs() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Wear_OS_Round_API_30"))
      .setType(DeviceType.WEAR_OS)
      .setName("Wear OS Round")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR, device.getIcon());
  }

  @Test
  public void buildTv() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Android_TV_1080p_API_30"))
      .setType(DeviceType.TV)
      .setName("Android TV (1080p)")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV, device.getIcon());
  }

  @Test
  public void buildAutomotive() {
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Automotive_1024p_landscape_API_30"))
      .setType(DeviceType.AUTOMOTIVE)
      .setName("Automotive (1024p landscape)")
      .setTarget("Android 11.0")
      .setCpuArchitecture("x86")
      .setAndroidVersion(new AndroidVersion(30))
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    assertEquals("x86", device.getCpuArchitecture());
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR, device.getIcon());
  }

  @Test
  public void newPairingStateCaseWearOsApiLevelIsLessThan28() {
    // Arrange
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Wear_OS_Small_Round_API_26"))
      .setType(DeviceType.WEAR_OS)
      .setName("Wear OS Small Round API 26")
      .setTarget("Android 8.0 Android Wear")
      .setCpuArchitecture("x86")
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    // Act
    boolean pairable = device.isPairable();
    Object message = device.getPairingMessage();

    // Assert
    assertFalse(pairable);
    assertEquals("Wear pairing requires API level >= 28", message);
  }

  @Test
  public void newPairingStateDefault() {
    // Arrange
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Android_TV_4K_API_31"))
      .setType(DeviceType.TV)
      .setName("Android TV (4K) API 31")
      .setTarget("Android 12.0 Google TV")
      .setCpuArchitecture("x86")
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    // Act
    boolean pairable = device.isPairable();
    Object message = device.getPairingMessage();

    // Assert
    assertFalse(pairable);
    assertNull(message);
  }
}
