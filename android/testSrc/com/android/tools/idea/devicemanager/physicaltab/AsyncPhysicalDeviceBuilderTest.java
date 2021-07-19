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
import static org.junit.Assert.assertNull;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.adb.AdbShellCommandResult;
import com.google.common.util.concurrent.Futures;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AsyncPhysicalDeviceBuilderTest {
  private final @NotNull IDevice myDevice;
  private final @NotNull AdbShellCommandExecutor myExecutor;

  public AsyncPhysicalDeviceBuilderTest() {
    myDevice = Mockito.mock(IDevice.class);

    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(Futures.immediateFuture("Pixel 3"));
    Mockito.when(myDevice.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(Futures.immediateFuture("Google"));
    Mockito.when(myDevice.getVersion()).thenReturn(new AndroidVersion(31));

    myExecutor = Mockito.mock(AdbShellCommandExecutor.class);
  }

  @Test
  public void newResolutionResultIsError() throws Exception {
    // Arrange
    Mockito.when(myExecutor.execute(myDevice, "wm size"))
      .thenReturn(new AdbShellCommandResult("wm size", Collections.singletonList("error"), true));

    AsyncPhysicalDeviceBuilder builder = new AsyncPhysicalDeviceBuilder(myDevice,
                                                                        new SerialNumber("86UX00F4R"),
                                                                        Instant.parse("2021-03-24T22:38:05.890570Z"),
                                                                        myExecutor);

    // Act
    Future<PhysicalDevice> future = builder.buildAsync();

    // Assert
    assertNull(future.get(256, TimeUnit.MILLISECONDS).getResolution());
  }

  @Test
  public void newResolution() throws Exception {
    // Arrange
    Mockito.when(myExecutor.execute(myDevice, "wm size"))
      .thenReturn(new AdbShellCommandResult("wm size", Arrays.asList("Physical size: 1080x2160", ""), false));

    AsyncPhysicalDeviceBuilder builder = new AsyncPhysicalDeviceBuilder(myDevice,
                                                                        new SerialNumber("86UX00F4R"),
                                                                        Instant.parse("2021-03-24T22:38:05.890570Z"),
                                                                        myExecutor);

    // Act
    Future<PhysicalDevice> future = builder.buildAsync();

    // Assert
    assertEquals(new Resolution(1080, 2160), future.get(256, TimeUnit.MILLISECONDS).getResolution());
  }
}
