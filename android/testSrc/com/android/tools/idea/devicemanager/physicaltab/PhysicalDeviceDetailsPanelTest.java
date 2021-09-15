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

import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceDetailsPanel.DeviceSection;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceDetailsPanel.DeviceSectionCallback;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceDetailsPanel.SummarySection;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceDetailsPanel.SummarySectionCallback;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalDeviceDetailsPanelTest {
  /**
   * PhysicalDeviceDetailsPanel accepts two devices; one of them is in a future
   *
   * <p>This device represents an instance built by AsyncPhysicalDeviceBuilder and contained in the table. They do not have all their
   * details filled in.
   *
   * <p>The devices in the test methods represent instances built by AsyncDetailsBuilder. When the details panel is shown the rest of the
   * details are filled in asynchronously.
   */
  private static final @NotNull PhysicalDevice DEVICE = new PhysicalDevice.Builder()
    .setKey(new SerialNumber("86UX00F4R"))
    .setName("Google Pixel 3")
    .setTarget("Android 12.0")
    .setApi("S")
    .addConnectionType(ConnectionType.USB)
    .build();

  @Test
  public void summarySectionCallbackOnSuccess() throws InterruptedException {
    // Arrange
    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setApi("S")
      .setResolution(new Resolution(1080, 2160))
      .setDensity(440)
      .addAllAbis(Arrays.asList("arm64-v8a", "armeabi-v7a", "armeabi"))
      .build();

    ListenableFuture<PhysicalDevice> future = Futures.immediateFuture(device);
    CountDownLatch latch = new CountDownLatch(1);

    // Act
    PhysicalDeviceDetailsPanel panel = new PhysicalDeviceDetailsPanel(DEVICE,
                                                                      future,
                                                                      section -> newSummarySectionCallback(section, latch),
                                                                      DeviceSectionCallback::new);

    // Assert
    CountDownLatchAssert.await(latch, Duration.ofMillis(4));

    SummarySection section = panel.getSummarySection();

    assertEquals("S", section.myApiLevelLabel.getText());
    assertEquals("1080 × 2160", section.myResolutionLabel.getText());
    assertEquals("393 × 786", section.myDpLabel.getText());
    assertEquals("arm64-v8a, armeabi-v7a, armeabi", section.myAbiListLabel.getText());
  }

  private static @NotNull FutureCallback<@NotNull PhysicalDevice> newSummarySectionCallback(@NotNull SummarySection section,
                                                                                            @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(new SummarySectionCallback(section), latch);
  }

  @Ignore
  @Test
  public void deviceSectionCallbackOnSuccess() throws InterruptedException {
    // Arrange
    ListenableFuture<PhysicalDevice> future = Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3);
    CountDownLatch latch = new CountDownLatch(1);

    // Act
    PhysicalDeviceDetailsPanel panel = new PhysicalDeviceDetailsPanel(DEVICE,
                                                                      future,
                                                                      SummarySectionCallback::new,
                                                                      section -> newDeviceSectionCallback(section, latch));

    // Assert
    CountDownLatchAssert.await(latch, Duration.ofMillis(4));
    assertEquals("Google Pixel 3", panel.getDeviceSection().myNameLabel.getText());
  }

  private static @NotNull FutureCallback<@NotNull PhysicalDevice> newDeviceSectionCallback(@NotNull DeviceSection section,
                                                                                           @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(new DeviceSectionCallback(section), latch);
  }
}
