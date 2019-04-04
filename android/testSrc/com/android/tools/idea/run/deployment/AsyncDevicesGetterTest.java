/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class AsyncDevicesGetterTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myGetter;
  private VirtualDevice myVirtualDevice;

  @Before
  public void setUp() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myGetter = new AsyncDevicesGetter(myRule.getProject(), new KeyToConnectionTimeMap(clock));

    myVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 27")
      .setKey("Pixel_2_XL_API_27")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();
  }

  @Test
  public void initChecker() {
    RunConfigurationModule configurationModule = Mockito.mock(RunConfigurationModule.class);
    Mockito.when(configurationModule.getModule()).thenReturn(myRule.getModule());

    ModuleBasedConfiguration configuration = Mockito.mock(ModuleBasedConfiguration.class);
    Mockito.when(configuration.getConfigurationModule()).thenReturn(configurationModule);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    myGetter.initChecker(configurationAndSettings, AsyncDevicesGetterTest::newAndroidFacet);
    assertNull(myGetter.getChecker());
  }

  @NotNull
  private static AndroidFacet newAndroidFacet(@NotNull Module module) {
    return new AndroidFacet(module, "Android", Mockito.mock(AndroidFacetConfiguration.class));
  }

  @Test
  public void newVirtualDeviceIfItsConnectedAvdNamesAreEqual() {
    IDevice ddmlibDevice = Mockito.mock(IDevice.class);
    Mockito.when(ddmlibDevice.getAvdName()).thenReturn("Pixel_2_XL_API_27");

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isRunning()).thenReturn(true);

    // noinspection UnstableApiUsage
    Mockito.when(androidDevice.getLaunchedDevice()).thenReturn(Futures.immediateFuture(ddmlibDevice));

    ConnectedDevice connectedDevice = new ConnectedDevice.Builder()
      .setName("Connected Device")
      .setKey("Pixel_2_XL_API_27")
      .setAndroidDevice(androidDevice)
      .build();

    Collection<ConnectedDevice> connectedDevices = new ArrayList<>(1);
    connectedDevices.add(connectedDevice);

    Device actualDevice = myGetter.newVirtualDeviceIfItsConnected(myVirtualDevice, connectedDevices);

    Object expectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 27")
      .setKey("Pixel_2_XL_API_27")
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(actualDevice.getAndroidDevice())
      .setConnected(true)
      .build();

    assertEquals(expectedDevice, actualDevice);
    assertEquals(Collections.emptyList(), connectedDevices);
  }

  @Test
  public void newVirtualDeviceIfItsConnected() {
    IDevice ddmlibDevice = Mockito.mock(IDevice.class);
    Mockito.when(ddmlibDevice.getAvdName()).thenReturn("Pixel_2_XL_API_28");

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isRunning()).thenReturn(true);

    // noinspection UnstableApiUsage
    Mockito.when(androidDevice.getLaunchedDevice()).thenReturn(Futures.immediateFuture(ddmlibDevice));

    ConnectedDevice connectedDevice = new ConnectedDevice.Builder()
      .setName("Connected Device")
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(androidDevice)
      .build();

    Collection<ConnectedDevice> connectedDevices = new ArrayList<>(1);
    connectedDevices.add(connectedDevice);

    Object actualDevice = myGetter.newVirtualDeviceIfItsConnected(myVirtualDevice, connectedDevices);

    assertSame(myVirtualDevice, actualDevice);

    // noinspection MisorderedAssertEqualsArguments
    assertEquals(Collections.singletonList(connectedDevice), connectedDevices);
  }
}
