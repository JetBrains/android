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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class BuilderServiceTest {
  private static final @NotNull Instant TIME = Instant.parse("2021-03-24T22:38:05.890570Z");

  private final @NotNull IDevice myDevice;
  private final @NotNull BuilderService myService;

  public BuilderServiceTest() {
    myDevice = Mockito.mock(IDevice.class);

    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(Futures.immediateFuture("Pixel 3"));
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(Futures.immediateFuture("Google"));
    Mockito.when(myDevice.getSerialNumber()).thenReturn("86UX00F4R");
    Mockito.when(myDevice.getVersion()).thenReturn(new AndroidVersion(30, "S"));

    myService = new BuilderService(Clock.fixed(TIME, ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void buildOnline() throws Exception {
    // Arrange
    Mockito.when(myDevice.isOnline()).thenReturn(true);

    // Act
    Future<PhysicalDevice> future = myService.build(myDevice);

    // Assert
    Object device = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .setLastOnlineTime(TIME)
      .setName("Google Pixel 3")
      .setOnline(true)
      .setTarget("Android 12 Preview")
      .build();

    assertEquals(device, future.get(256, TimeUnit.MILLISECONDS));
  }

  @Test
  public void build() throws Exception {
    // Act
    Future<PhysicalDevice> future = myService.build(myDevice);

    // Assert
    Object device = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .build();

    assertEquals(device, future.get(256, TimeUnit.MILLISECONDS));
  }
}
