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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.TestDeviceManagerFutures;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AsyncPhysicalDeviceBuilderTest {
  private final @NotNull IDevice myDevice;

  public AsyncPhysicalDeviceBuilderTest() {
    myDevice = Mockito.mock(IDevice.class);
    Mockito.when(myDevice.getVersion()).thenReturn(new AndroidVersion(31));
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(Futures.immediateFuture("Pixel 3"));
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(Futures.immediateFuture("Google"));
  }

  @Test
  public void getTypeStringIsNull() throws Exception {
    // Arrange
    AsyncPhysicalDeviceBuilder builder = new AsyncPhysicalDeviceBuilder(myDevice, new SerialNumber("86UX00F4R"));

    // Act
    Future<PhysicalDevice> future = builder.buildAsync();

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, TestDeviceManagerFutures.get(future));
  }

  @Test
  public void getType() throws Exception {
    // Arrange
    Mockito.when(myDevice.getProperty(IDevice.PROP_BUILD_CHARACTERISTICS)).thenReturn("nosdcard");
    AsyncPhysicalDeviceBuilder builder = new AsyncPhysicalDeviceBuilder(myDevice, new SerialNumber("86UX00F4R"));

    // Act
    Future<PhysicalDevice> future = builder.buildAsync();

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, TestDeviceManagerFutures.get(future));
  }

  @Test
  public void buildAsync() throws Exception {
    // Arrange
    Mockito.when(myDevice.isOnline()).thenReturn(true);
    AsyncPhysicalDeviceBuilder builder = new AsyncPhysicalDeviceBuilder(myDevice, new SerialNumber("86UX00F4R"));

    // Act
    Future<PhysicalDevice> future = builder.buildAsync();

    // Assert
    assertEquals(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3, TestDeviceManagerFutures.get(future));
  }
}
