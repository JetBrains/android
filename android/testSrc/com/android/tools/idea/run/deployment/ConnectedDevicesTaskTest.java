/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ConnectedDevicesTaskTest {
  private AndroidDebugBridge myAndroidDebugBridge;

  @Before
  public void mockAndroidDebugBridge() {
    myAndroidDebugBridge = Mockito.mock(AndroidDebugBridge.class);
  }

  @Test
  public void get() throws Exception {
    // Arrange
    IDevice device = Mockito.mock(IDevice.class);

    // noinspection UnstableApiUsage
    Mockito.when(myAndroidDebugBridge.getConnectedDevices()).thenReturn(Futures.immediateFuture(Collections.singletonList(device)));

    ConnectedDevicesTask task = new ConnectedDevicesTask(myAndroidDebugBridge, true, null, MoreExecutors.directExecutor(), d -> null);

    // Act
    Future<List<ConnectedDevice>> devices = task.get();

    // Assert
    assertEquals(Collections.emptyList(), devices.get(1, TimeUnit.SECONDS));
  }

  @Test
  public void getVirtualDeviceNameIsNull() throws Exception {
    // Arrange
    IDevice ddmlibDevice = Mockito.mock(IDevice.class);

    Mockito.when(ddmlibDevice.isOnline()).thenReturn(true);
    Mockito.when(ddmlibDevice.isEmulator()).thenReturn(true);
    Mockito.when(ddmlibDevice.getSerialNumber()).thenReturn("emulator-5554");

    // noinspection UnstableApiUsage
    Mockito.when(myAndroidDebugBridge.getConnectedDevices()).thenReturn(Futures.immediateFuture(Collections.singletonList(ddmlibDevice)));

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    AsyncSupplier task = new ConnectedDevicesTask(myAndroidDebugBridge, false, null, MoreExecutors.directExecutor(), d -> androidDevice);

    // Act
    Future connectedDevices = task.get();

    // Assert
    Object connectedDevice = new ConnectedDevice.Builder()
      .setName("Virtual Device")
      .setKey(new Key("emulator-5554"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(connectedDevice), connectedDevices.get(1, TimeUnit.SECONDS));
  }
}
