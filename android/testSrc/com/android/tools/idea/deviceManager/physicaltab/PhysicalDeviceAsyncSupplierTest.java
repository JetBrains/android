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
package com.android.tools.idea.deviceManager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
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
    Path adb = Jimfs.newFileSystem(Configuration.unix()).getPath("/home/user/Android/Sdk/platform-tools/adb");

    IDevice device = Mockito.mock(IDevice.class);
    Mockito.when(device.getSerialNumber()).thenReturn("86UX00F4R");

    AndroidDebugBridge bridge = Mockito.mock(AndroidDebugBridge.class);
    Mockito.when(bridge.isConnected()).thenReturn(true);
    Mockito.when(bridge.getDevices()).thenReturn(new IDevice[]{device});

    PhysicalDeviceAsyncSupplier supplier = new PhysicalDeviceAsyncSupplier(null,
                                                                           MoreExecutors.newDirectExecutorService(),
                                                                           project -> adb,
                                                                           path -> Futures.immediateFuture(bridge));

    // Act
    Future<List<PhysicalDevice>> future = supplier.get();

    // Assert
    assertEquals(Collections.singletonList(PhysicalDevice.newConnectedDevice("86UX00F4R")), future.get());
  }
}