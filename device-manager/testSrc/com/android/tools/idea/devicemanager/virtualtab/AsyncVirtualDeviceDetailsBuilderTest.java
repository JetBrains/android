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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.TestDeviceManagerFutures;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AsyncVirtualDeviceDetailsBuilderTest {
  private final @NotNull AvdInfo myAvd;
  private final @NotNull VirtualDevice myVirtualDevice;

  private final @NotNull IDevice myDevice;
  private final @NotNull AsyncVirtualDeviceDetailsBuilder myBuilder;

  public AsyncVirtualDeviceDetailsBuilderTest() {
    myAvd = Mockito.mock(AvdInfo.class);
    myVirtualDevice = TestVirtualDevices.pixel5Api31(myAvd);

    myDevice = Mockito.mock(IDevice.class);

    DeviceManagerAndroidDebugBridge bridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
    Mockito.when(bridge.getDevices(null)).thenReturn(Futures.immediateFuture(List.of(myDevice)));

    myBuilder = new AsyncVirtualDeviceDetailsBuilder(null, myVirtualDevice, bridge);
  }

  @Test
  public void buildAsyncAvdIsNull() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertEquals(myVirtualDevice, TestDeviceManagerFutures.get(future));
  }

  @Test
  public void buildAsync() throws Exception {
    // Arrange
    AvdData avd = new AvdData("Pixel_5_API_31", TestVirtualDevices.newKey("Pixel_5_API_31").toString());
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(avd));

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertEquals(myVirtualDevice, TestDeviceManagerFutures.get(future));
  }

  @Test
  public void getResolutionWidthIsEmptyOrHeightIsEmpty() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertNull(TestDeviceManagerFutures.get(future).getResolution());
  }

  @Test
  public void getResolution() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));

    Mockito.when(myAvd.getProperty("hw.lcd.width")).thenReturn("1080");
    Mockito.when(myAvd.getProperty("hw.lcd.height")).thenReturn("2340");

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertEquals(new Resolution(1080, 2340), TestDeviceManagerFutures.get(future).getResolution());
  }

  @Test
  public void getPropertyPropertyIsNull() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertEquals(-1, TestDeviceManagerFutures.get(future).getDensity());
  }

  @Test
  public void getProperty() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));
    Mockito.when(myAvd.getProperty("hw.lcd.density")).thenReturn("440");

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertEquals(440, TestDeviceManagerFutures.get(future).getDensity());
  }

  @Test
  public void getPropertyNumberFormatException() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));
    Mockito.when(myAvd.getProperty("hw.lcd.density")).thenReturn("notanint");

    // Act
    Future<Device> future = myBuilder.buildAsync();

    // Assert
    assertEquals(-1, TestDeviceManagerFutures.get(future).getDensity());
  }
}
