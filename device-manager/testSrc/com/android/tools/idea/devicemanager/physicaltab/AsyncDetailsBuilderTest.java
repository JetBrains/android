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
import com.android.tools.idea.device.Resolution;
import com.android.tools.idea.devicemanager.AdbShellCommandExecutor;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.SerialNumber;
import com.android.tools.idea.devicemanager.TestDeviceManagerFutures;
import com.google.common.util.concurrent.Futures;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AsyncDetailsBuilderTest {
  private final @NotNull IDevice myDevice;
  private final @NotNull AdbShellCommandExecutor myExecutor;
  private final @NotNull AsyncDetailsBuilder myBuilder;

  public AsyncDetailsBuilderTest() {
    myDevice = Mockito.mock(IDevice.class);

    Mockito.when(myDevice.getSerialNumber()).thenReturn("86UX00F4R");
    Mockito.when(myDevice.getDensity()).thenReturn(-1);

    DeviceManagerAndroidDebugBridge bridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
    Mockito.when(bridge.getDevices(null)).thenReturn(Futures.immediateFuture(Collections.singletonList(myDevice)));

    myExecutor = Mockito.mock(AdbShellCommandExecutor.class);
    myBuilder = new AsyncDetailsBuilder(null, TestPhysicalDevices.GOOGLE_PIXEL_3, bridge, myExecutor);
  }

  @Test
  public void buildAsync() throws Exception {
    // Arrange
    Mockito.when(myExecutor.execute(myDevice, "wm size")).thenReturn(Optional.of(Arrays.asList("Physical size: 1080x2160", "")));

    // Act
    Future<PhysicalDevice> future = myBuilder.buildAsync();

    // Assert
    Object device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setAndroidVersion(new AndroidVersion(31))
      .setResolution(new Resolution(1080, 2160))
      .build();

    assertEquals(device, TestDeviceManagerFutures.get(future));
  }
}
