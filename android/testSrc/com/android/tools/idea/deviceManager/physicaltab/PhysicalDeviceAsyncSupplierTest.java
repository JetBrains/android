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
package com.android.tools.idea.deviceManager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceAsyncSupplierTest {
  @Test
  public void get() throws Exception {
    // Arrange
    Path adb = Jimfs.newFileSystem(Configuration.unix()).getPath("/home/user/Android/Sdk/platform-tools/adb");

    IDevice device = Mockito.mock(IDevice.class);
    Mockito.when(device.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(Futures.immediateFuture("Pixel 3"));
    Mockito.when(device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(Futures.immediateFuture("Google"));
    Mockito.when(device.getSerialNumber()).thenReturn("86UX00F4R");

    AndroidDebugBridge bridge = Mockito.mock(AndroidDebugBridge.class);
    Mockito.when(bridge.isConnected()).thenReturn(true);
    Mockito.when(bridge.getDevices()).thenReturn(new IDevice[]{device});

    Instant onlineTime = Instant.parse("2021-03-24T22:38:05.890570Z");

    OnlineTimeService service = new OnlineTimeService(Clock.fixed(onlineTime, ZoneId.of("America/Los_Angeles")));

    PhysicalDeviceAsyncSupplier supplier = new PhysicalDeviceAsyncSupplier(null,
                                                                           MoreExecutors.newDirectExecutorService(),
                                                                           project -> adb,
                                                                           path -> Futures.immediateFuture(bridge),
                                                                           () -> service);

    // Act
    Future<List<PhysicalDevice>> future = supplier.get();

    // Assert
    Object physicalDevice = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .setLastOnlineTime(onlineTime)
      .setName("Google Pixel 3")
      .setOnline(true)
      .build();

    assertEquals(Collections.singletonList(physicalDevice), future.get());
  }
}
