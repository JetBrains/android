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
import com.google.common.util.concurrent.Futures;
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
    IDevice device = Mockito.mock(IDevice.class);

    DeviceManagerAndroidDebugBridge bridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
    Mockito.when(bridge.getDevices(null)).thenReturn(Futures.immediateFuture(Collections.singletonList(device)));

    BuilderService service = Mockito.mock(BuilderService.class);
    Mockito.when(service.build(device)).thenReturn(Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3));

    PhysicalDeviceAsyncSupplier supplier = new PhysicalDeviceAsyncSupplier(null, bridge, () -> service);

    // Act
    Future<List<PhysicalDevice>> devicesFuture = supplier.get();

    // Assert
    assertEquals(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3), DeviceManagerFutures.get(devicesFuture));
  }
}
