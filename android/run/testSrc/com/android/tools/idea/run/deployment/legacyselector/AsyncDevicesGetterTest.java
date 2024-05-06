/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.deployment.legacyselector.Device.Type;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.util.Disposer;
import icons.StudioIcons;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class AsyncDevicesGetterTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myGetter;

  @Before
  public void setUp() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myGetter = new AsyncDevicesGetter(myRule.getProject(), new KeyToConnectionTimeMap(clock));
    Disposer.register(myRule.getTestRootDisposable(), myGetter);
  }

  @Test
  public void getImpl() {
    // Arrange
    AndroidDevice pixel2ApiQAndroidDevice = Mockito.mock(AndroidDevice.class);

    VirtualDevice pixel2ApiQVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API Q")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_2_API_Q)
      .setAndroidDevice(pixel2ApiQAndroidDevice)
      .setNameKey(new VirtualDeviceName("Pixel_2_API_Q"))
      .build();

    VirtualDevice pixel3ApiQVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API Q")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_3_API_Q)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setNameKey(new VirtualDeviceName("Pixel_3_API_Q"))
      .build();

    AndroidDevice googlePixel3AndroidDevice = Mockito.mock(AndroidDevice.class);

    ConnectedDevice googlePixel3ConnectedDevice = new ConnectedDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("Google Pixel 3")
      .setAndroidDevice(googlePixel3AndroidDevice)
      .build();

    AndroidDevice pixel3ApiQAndroidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(pixel3ApiQAndroidDevice.isVirtual()).thenReturn(true);

    ConnectedDevice pixel3ApiQConnectedDevice = new ConnectedDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_3_API_Q"))
      .setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
      .setName("Virtual Device")
      .setAndroidDevice(pixel3ApiQAndroidDevice)
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(
      Arrays.asList(pixel2ApiQVirtualDevice, pixel3ApiQVirtualDevice),
      Arrays.asList(googlePixel3ConnectedDevice, pixel3ApiQConnectedDevice));

    // Assert
    Object expectedPixel3ApiQDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API Q")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_3_API_Q)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(pixel3ApiQAndroidDevice)
      .setNameKey(new VirtualDeviceName("Pixel_3_API_Q"))
      .build();

    Object expectedGooglePixel3Device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setName("Google Pixel 3")
      .setAndroidDevice(googlePixel3AndroidDevice)
      .build();

    Object expectedPixel2ApiQDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API Q")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_2_API_Q)
      .setAndroidDevice(pixel2ApiQAndroidDevice)
      .setNameKey(new VirtualDeviceName("Pixel_2_API_Q"))
      .build();

    assertEquals(Arrays.asList(expectedPixel3ApiQDevice, expectedGooglePixel3Device, expectedPixel2ApiQDevice), actualDevices);
  }

  @Test
  public void getImplDeveloperBuiltTheirOwnSystemImage() {
    // Arrange
    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isVirtual()).thenReturn(true);

    ConnectedDevice connectedDevice = new ConnectedDevice.Builder()
      .setKey(new SerialNumber("emulator-5554"))
      .setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
      .setName("Virtual Device")
      .setAndroidDevice(androidDevice)
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(Collections.emptyList(), Collections.singletonList(connectedDevice));

    // Assert
    Object expectedDevice = new VirtualDevice.Builder()
      .setName("Virtual Device")
      .setType(Type.PHONE)
      .setKey(new SerialNumber("emulator-5554"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(expectedDevice), actualDevices);
  }

  @Test
  public void getImplConnectedDeviceHasPathKey() {
    // Arrange
    var virtualDevice = TestDevices.buildPixel4Api30();

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isVirtual()).thenReturn(true);

    ConnectedDevice connectedDevice = new ConnectedDevice.Builder()
      .setKey(Keys.PIXEL_4_API_30)
      .setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
      .setName("Pixel_4_API_30")
      .setAndroidDevice(androidDevice)
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(Collections.singletonList(virtualDevice), Collections.singletonList(connectedDevice));

    // Assert
    Object expectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(expectedDevice), actualDevices);
  }

  @Test
  public void getImplConnectedDeviceHasNameKey() {
    // Arrange
    VirtualDevice virtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isVirtual()).thenReturn(true);

    ConnectedDevice connectedDevice = new ConnectedDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_4_API_30"))
      .setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
      .setName("Pixel_4_API_30")
      .setAndroidDevice(androidDevice)
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(Collections.singletonList(virtualDevice), Collections.singletonList(connectedDevice));

    // Assert
    Object expectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(androidDevice)
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    assertEquals(Collections.singletonList(expectedDevice), actualDevices);
  }
}
