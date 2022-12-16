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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ConnectedDevicesTask2Test {
  private final @NotNull IDevice myDevice;
  private final @NotNull AsyncSupplier<@NotNull Collection<@NotNull ConnectedDevice>> myTask;

  public ConnectedDevicesTask2Test() {
    myDevice = Mockito.mock(IDevice.class);
    Mockito.when(myDevice.isOnline()).thenReturn(true);

    var bridge = Mockito.mock(AndroidDebugBridge.class);
    Mockito.when(bridge.getConnectedDevices()).thenReturn(Futures.immediateFuture(List.of(myDevice)));

    myTask = new ConnectedDevicesTask2(bridge);
  }

  @Test
  public void getNameNameIsNull() throws Exception {
    // Arrange
    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(new AvdData(null, null)));
    Mockito.when(myDevice.getSerialNumber()).thenReturn("emulator-5554");

    // Act
    var future = myTask.get();

    // Assert
    assertEquals("emulator-5554", ((List<ConnectedDevice>)future.get(60, TimeUnit.SECONDS)).get(0).getName());
  }

  @Test
  public void getNameNameEqualsBuild() throws Exception {
    // Arrange
    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(new AvdData("<build>", null)));
    Mockito.when(myDevice.getSerialNumber()).thenReturn("emulator-5554");

    // Act
    var future = myTask.get();

    // Assert
    assertEquals("emulator-5554", ((List<ConnectedDevice>)future.get(60, TimeUnit.SECONDS)).get(0).getName());
  }

  @Test
  public void getName() throws Exception {
    // Arrange
    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(new AvdData("Pixel_6_API_33", null)));
    Mockito.when(myDevice.getSerialNumber()).thenReturn("emulator-5554");

    // Act
    var future = myTask.get();

    // Assert
    assertEquals("Pixel_6_API_33", ((List<ConnectedDevice>)future.get(60, TimeUnit.SECONDS)).get(0).getName());
  }

  @Test
  public void getNameAsync() throws Exception {
    // Arrange
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(Futures.immediateFuture("Pixel 4a"));
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(Futures.immediateFuture("Google"));

    // Act
    var future = myTask.get();

    // Assert
    assertEquals("Google Pixel 4a", ((List<ConnectedDevice>)future.get(60, TimeUnit.SECONDS)).get(0).getName());
  }
}
