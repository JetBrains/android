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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ConnectedDevicesTaskTest {
  private IDevice myDdmlibDevice;
  private AndroidDebugBridge myAndroidDebugBridge;

  private Executor myExecutor;

  @Before
  public void mockAndroidDebugBridge() {
    myDdmlibDevice = Mockito.mock(IDevice.class);

    myAndroidDebugBridge = Mockito.mock(AndroidDebugBridge.class);
    Mockito.when(myAndroidDebugBridge.getConnectedDevices()).thenReturn(Futures.immediateFuture(Collections.singletonList(myDdmlibDevice)));
  }

  @Before
  public void initExecutor() {
    myExecutor = MoreExecutors.directExecutor();
  }

  @Test
  public void get() throws Exception {
    // Arrange
    AsyncSupplier<List<ConnectedDevice>> task = new ConnectedDevicesTask(myAndroidDebugBridge, null, myExecutor, d -> null);

    // Act
    Future<List<ConnectedDevice>> devices = task.get();

    // Assert
    assertEquals(Collections.emptyList(), devices.get(1, TimeUnit.SECONDS));
  }

  @Test
  public void getVirtualDeviceNameIsNull() throws Exception {
    // Arrange
    Mockito.when(myDdmlibDevice.isOnline()).thenReturn(true);
    Mockito.when(myDdmlibDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDdmlibDevice.getSerialNumber()).thenReturn("emulator-5554");

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    AsyncSupplier<List<ConnectedDevice>> task = new ConnectedDevicesTask(myAndroidDebugBridge, null, myExecutor, d -> androidDevice);

    // Act
    Future<List<ConnectedDevice>> connectedDevices = task.get();

    // Assert
    Object connectedDevice = new ConnectedDevice.Builder()
      .setName("emulator-5554")
      .setKey(new SerialNumber("emulator-5554"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(connectedDevice), connectedDevices.get(1, TimeUnit.SECONDS));
  }

  @Test
  public void newKeyDeviceIsntEmulator() throws Exception {
    // Arrange
    Mockito.when(myDdmlibDevice.isOnline()).thenReturn(true);
    Mockito.when(myDdmlibDevice.getSerialNumber()).thenReturn("86UX00F4R");

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    AsyncSupplier<List<ConnectedDevice>> task = new ConnectedDevicesTask(myAndroidDebugBridge, null, myExecutor, d -> androidDevice);

    // Act
    Future<List<ConnectedDevice>> connectedDevices = task.get();

    // Assert
    Object connectedDevice = new ConnectedDevice.Builder()
      .setName("86UX00F4R")
      .setKey(new SerialNumber("86UX00F4R"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(connectedDevice), connectedDevices.get(1, TimeUnit.SECONDS));
  }

  @Test
  public void newKeyPathIsntNull() throws Exception {
    // Arrange
    Mockito.when(myDdmlibDevice.isOnline()).thenReturn(true);
    Mockito.when(myDdmlibDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDdmlibDevice.getAvdName()).thenReturn("Pixel_4_API_30");
    Mockito.when(myDdmlibDevice.getAvdPath()).thenReturn("/home/juancnuno/.android/avd/Pixel_4_API_30.avd");

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    AsyncSupplier<List<ConnectedDevice>> task = new ConnectedDevicesTask(myAndroidDebugBridge, null, myExecutor, d -> androidDevice);

    // Act
    Future<List<ConnectedDevice>> connectedDevices = task.get();

    // Assert
    Object connectedDevice = new ConnectedDevice.Builder()
      .setName("Pixel_4_API_30")
      .setKey(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(connectedDevice), connectedDevices.get(1, TimeUnit.SECONDS));
  }

  @Test
  public void newKeyNameIsntNullAndDoesntEqualBuild() throws Exception {
    // Arrange
    Mockito.when(myDdmlibDevice.isOnline()).thenReturn(true);
    Mockito.when(myDdmlibDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDdmlibDevice.getAvdName()).thenReturn("Pixel_4_API_30");

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    AsyncSupplier<List<ConnectedDevice>> task = new ConnectedDevicesTask(myAndroidDebugBridge, null, myExecutor, d -> androidDevice);

    // Act
    Future<List<ConnectedDevice>> connectedDevices = task.get();

    // Assert
    Object connectedDevice = new ConnectedDevice.Builder()
      .setName("Pixel_4_API_30")
      .setKey(new VirtualDeviceName("Pixel_4_API_30"))
      .setAndroidDevice(androidDevice)
      .build();

    assertEquals(Collections.singletonList(connectedDevice), connectedDevices.get(1, TimeUnit.SECONDS));
  }
}
