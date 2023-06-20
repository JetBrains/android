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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.device.Resolution;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.SerialNumber;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceDetailsPanel.SummarySection;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.DisposableRule;
import java.awt.Container;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceDetailsPanelTest {
  private final @NotNull AsyncDetailsBuilder myBuilder = Mockito.mock(AsyncDetailsBuilder.class);
  private final @NotNull WearPairingManager myManager = Mockito.mock(WearPairingManager.class);

  @Rule
  public final DisposableRule myRule = new DisposableRule();

  @Test
  public void summarySectionCallbackOnSuccess() throws InterruptedException {
    // Arrange
    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setAndroidVersion(new AndroidVersion(31))
      .setResolution(new Resolution(1080, 2160))
      .setDensity(440)
      .addAllAbis(Arrays.asList("arm64-v8a", "armeabi-v7a", "armeabi"))
      .build();

    Mockito.when(myBuilder.buildAsync()).thenReturn(Futures.immediateFuture(device));
    CountDownLatch latch = new CountDownLatch(1);

    // Act
    PhysicalDeviceDetailsPanel panel = new PhysicalDeviceDetailsPanel(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3,
                                                                      myBuilder,
                                                                      myManager,
                                                                      section -> newSummarySectionCallback(section, latch));

    Disposer.register(myRule.getDisposable(), panel);

    // Assert
    CountDownLatchAssert.await(latch);

    SummarySection section = panel.getSummarySection();

    assertEquals("31", section.myApiLevelLabel.getText());
    assertEquals("1080 × 2160", section.myResolutionLabel.getText());
    assertEquals("393 × 786", section.myDpLabel.getText());
    assertEquals("arm64-v8a, armeabi-v7a, armeabi", section.myAbiListLabel.getText());
  }

  @NotNull
  private static FutureCallback<PhysicalDevice> newSummarySectionCallback(@NotNull SummarySection section, @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(PhysicalDeviceDetailsPanel.newSummarySectionCallback(section), latch);
  }

  @Test
  public void setInfoSectionPanelLayout() {
    // Arrange
    Mockito.when(myBuilder.buildAsync()).thenReturn(Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    DetailsPanel detailsPanel = new PhysicalDeviceDetailsPanel(TestPhysicalDevices.GOOGLE_PIXEL_3, myBuilder, myManager);

    // Assert
    Container sectionPanel = detailsPanel.getInfoSectionPanel();

    assertEquals(1, sectionPanel.getComponentCount());
    assertEquals("Details unavailable for offline devices", ((JLabel)sectionPanel.getComponent(0)).getText());
  }
}
