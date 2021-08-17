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
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceChangeListener.AddOrSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceChangeListenerTest {
  private final @NotNull PhysicalDeviceTableModel myModel = Mockito.mock(PhysicalDeviceTableModel.class);
  private final @NotNull AndroidDebugBridge myBridge = Mockito.mock(AndroidDebugBridge.class);
  private final @NotNull IDevice myDevice = Mockito.mock(IDevice.class);
  private final @NotNull BuilderService myService = Mockito.mock(BuilderService.class);

  @Test
  public void deviceChangedMaskEqualsChangeProfileableClientList() {
    // Arrange
    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, new AddOrSet(myModel));

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_PROFILEABLE_CLIENT_LIST);

    // Assert
    Mockito.verify(myModel, Mockito.never()).addOrSet(ArgumentMatchers.any());
  }

  @Test
  public void deviceChanged() throws InterruptedException {
    // Arrange
    PhysicalDevice physicalDevice = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .build();

    Mockito.when(myService.build(myDevice)).thenReturn(Futures.immediateFuture(physicalDevice));

    CountDownLatch latch = new CountDownLatch(1);

    FutureCallback<PhysicalDevice> callback = new CountDownLatchAddOrSet(myModel, latch);
    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, callback);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    waitFor(latch, Duration.ofMillis(512));
    Mockito.verify(myModel).addOrSet(physicalDevice);
  }

  private static final class CountDownLatchAddOrSet extends AddOrSet {
    private final @NotNull CountDownLatch myLatch;

    private CountDownLatchAddOrSet(@NotNull PhysicalDeviceTableModel model, @NotNull CountDownLatch latch) {
      super(model);
      myLatch = latch;
    }

    @Override
    public void onSuccess(@Nullable PhysicalDevice device) {
      super.onSuccess(device);
      myLatch.countDown();
    }
  }

  private static void waitFor(@NotNull CountDownLatch latch, @NotNull Duration duration) throws InterruptedException {
    if (!latch.await(duration.toMillis(), TimeUnit.MILLISECONDS)) {
      Assert.fail();
    }
  }
}
