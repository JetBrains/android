/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.IDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DdmlibAndroidDebugBridgeTest {
  private final @NotNull ListeningExecutorService myExecutorService = MoreExecutors.newDirectExecutorService();

  @Test
  public void getDevicesBridgeIsNull() throws Exception {
    // Arrange
    AndroidDebugBridge bridge = new DdmlibAndroidDebugBridge(myExecutorService, () -> null, adb -> Futures.immediateFuture(null));

    // Act
    Future<Collection<IDevice>> devices = bridge.getConnectedDevices();

    // Assert
    assertEquals(Collections.emptyList(), devices.get());
  }

  @Test
  public void getDevicesBridgeIsntConnected() throws Exception {
    // Arrange
    Path adb = Jimfs.newFileSystem(Configuration.unix()).getPath("/home/user/Android/Sdk/platform-tools/adb");
    com.android.ddmlib.AndroidDebugBridge ddmlibBridge = Mockito.mock(com.android.ddmlib.AndroidDebugBridge.class);

    AndroidDebugBridge bridge = new DdmlibAndroidDebugBridge(myExecutorService, () -> adb, a -> Futures.immediateFuture(ddmlibBridge));

    // Act
    Future<Collection<IDevice>> devices = bridge.getConnectedDevices();

    // Assert
    assertEquals(Collections.emptyList(), devices.get());
  }

  @Test
  public void getDevices() throws Exception {
    // Arrange
    Path adb = Jimfs.newFileSystem(Configuration.unix()).getPath("/home/user/Android/Sdk/platform-tools/adb");

    com.android.ddmlib.AndroidDebugBridge ddmlibBridge = Mockito.mock(com.android.ddmlib.AndroidDebugBridge.class);
    Mockito.when(ddmlibBridge.isConnected()).thenReturn(true);
    Mockito.when(ddmlibBridge.getDevices()).thenReturn(new IDevice[0]);

    AndroidDebugBridge bridge = new DdmlibAndroidDebugBridge(myExecutorService, () -> adb, a -> Futures.immediateFuture(ddmlibBridge));

    // Act
    Future<Collection<IDevice>> devices = bridge.getConnectedDevices();

    // Assert
    assertEquals(Collections.emptyList(), devices.get());
  }
}
