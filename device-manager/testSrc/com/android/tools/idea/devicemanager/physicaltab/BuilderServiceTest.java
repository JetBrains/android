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
import com.google.common.util.concurrent.Futures;
import java.util.EnumSet;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class BuilderServiceTest {
  private final @NotNull IDevice myDevice;
  private final @NotNull BuilderService myService;

  public BuilderServiceTest() {
    myDevice = Mockito.mock(IDevice.class);

    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(Futures.immediateFuture("Pixel 3"));
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(Futures.immediateFuture("Google"));
    Mockito.when(myDevice.getVersion()).thenReturn(new AndroidVersion(31));
    Mockito.when(myDevice.getDensity()).thenReturn(-1);

    myService = new BuilderService();
  }

  @Test
  public void buildOnline() throws Exception {
    // Arrange
    Mockito.when(myDevice.isOnline()).thenReturn(true);
    Mockito.when(myDevice.getSerialNumber()).thenReturn("86UX00F4R");

    // Act
    Future<PhysicalDevice> future = myService.build(myDevice);

    // Assert
    assertEquals(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3, DeviceManagerFutures.get(future));
  }

  @Test
  public void build() throws Exception {
    // Arrange
    Mockito.when(myDevice.getSerialNumber()).thenReturn("86UX00F4R");

    // Act
    Future<PhysicalDevice> future = myService.build(myDevice);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, DeviceManagerFutures.get(future));
  }

  @Test
  public void buildMdnsAutoConnectTls() throws Exception {
    // Arrange
    Mockito.when(myDevice.isOnline()).thenReturn(true);
    Mockito.when(myDevice.getSerialNumber()).thenReturn("adb-86UX00F4R-cYuns7._adb-tls-connect._tcp");

    // Act
    Future<PhysicalDevice> future = myService.build(myDevice);

    // Assert
    assertEquals(EnumSet.of(ConnectionType.WI_FI), DeviceManagerFutures.get(future).getConnectionTypes());
  }
}
