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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.devicemanager.virtualtab.TestVirtualDevices;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceManagerAndroidDebugBridgeTest {
  private final @NotNull IDevice myDevice;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;

  public DeviceManagerAndroidDebugBridgeTest() {
    myDevice = Mockito.mock(IDevice.class);

    AndroidDebugBridge bridge = Mockito.mock(AndroidDebugBridge.class);
    Mockito.when(bridge.isConnected()).thenReturn(true);
    Mockito.when(bridge.getDevices()).thenReturn(new IDevice[]{myDevice});

    myBridge = new DeviceManagerAndroidDebugBridge(project -> Futures.immediateFuture(bridge));
  }

  @Test
  public void findDeviceAvdIsNull() throws Exception {
    // Arrange
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));

    // Act
    Future<IDevice> future = myBridge.findDevice(null, TestVirtualDevices.PIXEL_5_API_31_KEY);

    // Assert
    assertNull(TestDeviceManagerFutures.get(future));
  }

  @Test
  public void findDevice() throws Exception {
    // Arrange
    AvdData avd = new AvdData("Pixel_5_API_31", TestVirtualDevices.PIXEL_5_API_31_KEY.toString());
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(avd));

    // Act
    Future<IDevice> future = myBridge.findDevice(null, TestVirtualDevices.PIXEL_5_API_31_KEY);

    // Assert
    assertEquals(myDevice, TestDeviceManagerFutures.get(future));
  }
}
