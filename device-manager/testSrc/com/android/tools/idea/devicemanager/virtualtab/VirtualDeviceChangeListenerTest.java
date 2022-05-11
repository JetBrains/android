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

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.Key;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceChangeListenerTest {
  private final @NotNull VirtualDeviceTableModel myModel = Mockito.mock(VirtualDeviceTableModel.class);
  private final @NotNull IDevice myDevice = Mockito.mock(IDevice.class);

  @Test
  public void deviceChangedDeviceIsntVirtualDevice() {
    // Arrange
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel);

    // Act
    listener.deviceChanged(myDevice, 0);

    // Assert
    Mockito.verify(myModel, Mockito.never()).setOnline(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
  }

  @Test
  public void deviceChangedChangeStateIsntSet() {
    // Arrange
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel);
    Mockito.when(myDevice.isEmulator()).thenReturn(true);

    // Act
    listener.deviceChanged(myDevice, 0);

    // Assert
    Mockito.verify(myModel, Mockito.never()).setOnline(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
  }

  @Test
  public void deviceChangedStateIsNull() {
    // Arrange
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel);
    Mockito.when(myDevice.isEmulator()).thenReturn(true);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    Mockito.verify(myModel, Mockito.never()).setOnline(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
  }

  @Test
  public void deviceChangedAvdIsNull() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel, (model, online) -> newSetOnline(model, online, latch));

    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getState()).thenReturn(DeviceState.OFFLINE);
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(null));

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(myModel).setAllOnline();
  }

  @Test
  public void deviceChangedNameIsNull() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel, (model, online) -> newSetOnline(model, online, latch));

    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getState()).thenReturn(DeviceState.OFFLINE);
    Mockito.when(myDevice.getAvdData()).thenReturn(Futures.immediateFuture(new AvdData(null, null)));

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(myModel).setAllOnline();
  }

  @Test
  public void deviceChangedCaseOffline() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel, (model, online) -> newSetOnline(model, online, latch));

    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getState()).thenReturn(DeviceState.OFFLINE);
    Mockito.when(myDevice.getAvdData())
      .thenReturn(Futures.immediateFuture(new AvdData("Pixel_6_API_31", "/usr/local/google/home/user/.android/avd/Pixel_6_API_31.avd")));

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(myModel).setOnline(new VirtualDeviceName("/usr/local/google/home/user/.android/avd/Pixel_6_API_31.avd"), false);
  }

  @Test
  public void deviceChanged() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel, (model, online) -> newSetOnline(model, online, latch));

    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getState()).thenReturn(DeviceState.ONLINE);
    Mockito.when(myDevice.getAvdData())
      .thenReturn(Futures.immediateFuture(new AvdData("Pixel_6_API_31", "/usr/local/google/home/user/.android/avd/Pixel_6_API_31.avd")));

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(myModel).setOnline(new VirtualDeviceName("/usr/local/google/home/user/.android/avd/Pixel_6_API_31.avd"), true);
  }

  private static @NotNull FutureCallback<@NotNull Key> newSetOnline(@NotNull VirtualDeviceTableModel model,
                                                                    boolean online,
                                                                    @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(VirtualDeviceChangeListener.newSetOnline(model, online), latch);
  }
}
