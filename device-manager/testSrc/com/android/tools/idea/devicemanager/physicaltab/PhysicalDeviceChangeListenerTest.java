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

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.concurrency.CountDownLatchAssert;
import com.android.tools.idea.concurrency.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceChangeListenerTest {
  private final @NotNull PhysicalDeviceTableModel myModel = Mockito.mock(PhysicalDeviceTableModel.class);
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
  private final @NotNull IDevice myDevice = Mockito.mock(IDevice.class);
  private final @NotNull BuilderService myService = Mockito.mock(BuilderService.class);

  @Test
  public void deviceChangedMaskEqualsChangeProfileableClientList() {
    // Arrange
    FutureCallback<PhysicalDevice> callback = PhysicalDeviceChangeListener.newAddOrSet(myModel);
    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, callback);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_PROFILEABLE_CLIENT_LIST);

    // Assert
    Mockito.verify(myModel, Mockito.never()).addOrSet(Mockito.any());
  }

  @Test
  public void deviceChanged() throws InterruptedException {
    // Arrange
    Mockito.when(myService.build(myDevice)).thenReturn(Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3));

    CountDownLatch latch = new CountDownLatch(1);

    FutureCallback<PhysicalDevice> callback = new CountDownLatchFutureCallback<>(PhysicalDeviceChangeListener.newAddOrSet(myModel), latch);
    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, callback);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(myModel).addOrSet(TestPhysicalDevices.GOOGLE_PIXEL_3);
  }
}
