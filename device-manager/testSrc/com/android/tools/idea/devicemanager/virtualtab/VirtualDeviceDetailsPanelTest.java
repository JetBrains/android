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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.device.Resolution;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.InfoSection;
import com.android.tools.idea.devicemanager.StorageDevice;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceDetailsPanel.SummarySection;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceDetailsPanelTest {
  private final @NotNull AvdInfo myAvd = Mockito.mock(AvdInfo.class);

  @Test
  public void initSummarySection() throws Exception {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);
    VirtualDevice virtualDevice = TestVirtualDevices.onlinePixel5Api31(myAvd);

    Device device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.PIXEL_5_API_31_KEY)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setResolution(new Resolution(1080, 2340))
      .setDensity(440)
      .addAllAbis(List.of("x86_64", "arm64-v8a"))
      .setStorageDevice(new StorageDevice(5_333))
      .setAvdInfo(myAvd)
      .build();

    AsyncVirtualDeviceDetailsBuilder builder = mock(device);
    CountDownLatch latch = new CountDownLatch(1);

    // Act
    VirtualDeviceDetailsPanel panel = new VirtualDeviceDetailsPanel(virtualDevice,
                                                                    builder,
                                                                    section -> newSummarySectionCallback(section, latch));

    // Assert
    CountDownLatchAssert.await(latch);

    SummarySection section = panel.getSummarySection();

    assertEquals("31", section.myApiLevelLabel.getText());
    assertEquals("1080 × 2340", section.myResolutionLabel.getText());
    assertEquals("393 × 851", section.myDpLabel.getText());
    assertEquals("x86_64, arm64-v8a", section.myAbiListLabel.getText());
    assertEquals("5,333 MB", section.myAvailableStorageLabel.getText());
  }

  private static @NotNull FutureCallback<Device> newSummarySectionCallback(@NotNull SummarySection section,
                                                                                    @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(VirtualDeviceDetailsPanel.newSummarySectionCallback(section), latch);
  }

  @Test
  public void initSummarySectionStatusDoesntEqualOk() {
    // Arrange
    Object configIni = Paths.get(System.getProperty("user.home"), ".android", "avd", "Pixel_5_API_31.avd", "config.ini");

    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.ERROR_PROPERTIES);
    Mockito.when(myAvd.getErrorMessage()).thenReturn("Failed to parse properties from " + configIni);

    VirtualDevice virtualDevice = TestVirtualDevices.pixel5Api31(myAvd);
    AsyncVirtualDeviceDetailsBuilder builder = mock(virtualDevice);

    // Act
    VirtualDeviceDetailsPanel panel = new VirtualDeviceDetailsPanel(virtualDevice,
                                                                    builder,
                                                                    VirtualDeviceDetailsPanel::newSummarySectionCallback);

    // Assert
    assert panel.getSummarySection().myErrorLabel != null;
    assertEquals("Failed to parse properties from " + configIni, panel.getSummarySection().myErrorLabel.getText());
  }

  @Test
  public void initPropertiesSection() {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);

    Mockito.when(myAvd.getProperties()).thenReturn(Map.of("fastboot.chosenSnapshotFile", "",
                                                          "runtime.network.speed", "full",
                                                          "hw.accelerometer", "yes"));

    VirtualDevice virtualDevice = TestVirtualDevices.pixel5Api31(myAvd);
    AsyncVirtualDeviceDetailsBuilder builder = mock(virtualDevice);

    // Act
    VirtualDeviceDetailsPanel panel = new VirtualDeviceDetailsPanel(virtualDevice,
                                                                    builder,
                                                                    VirtualDeviceDetailsPanel::newSummarySectionCallback);

    // Assert
    InfoSection section = panel.getPropertiesSection();

    assertEquals(List.of("fastboot.chosenSnapshotFile", "hw.accelerometer", "runtime.network.speed"), section.getNames());
    assertEquals(List.of("", "yes", "full"), section.getValues());
  }

  private static @NotNull AsyncVirtualDeviceDetailsBuilder mock(@NotNull Device device) {
    AsyncVirtualDeviceDetailsBuilder builder = Mockito.mock(AsyncVirtualDeviceDetailsBuilder.class);
    Mockito.when(builder.buildAsync()).thenReturn(Futures.immediateFuture(device));

    return builder;
  }
}
