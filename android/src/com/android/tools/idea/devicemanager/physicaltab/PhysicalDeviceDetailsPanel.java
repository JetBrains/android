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

import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.InfoSection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.concurrent.Executor;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceDetailsPanel extends DetailsPanel {
  private final @NotNull SummarySection mySummarySection;
  private final @NotNull DeviceSection myDeviceSection;

  @VisibleForTesting
  static final class SummarySection extends InfoSection {
    @VisibleForTesting final @NotNull JLabel myApiLevelLabel;
    @VisibleForTesting final @NotNull JLabel myResolutionLabel;
    @VisibleForTesting final @NotNull JLabel myDpLabel;
    @VisibleForTesting final @NotNull JLabel myAbiListLabel;

    private SummarySection() {
      super("Summary");

      myApiLevelLabel = addNameAndValueLabels("API level");
      myResolutionLabel = addNameAndValueLabels("Resolution");
      myDpLabel = addNameAndValueLabels("dp");
      myAbiListLabel = addNameAndValueLabels("ABI list");

      setLayout();
    }
  }

  @VisibleForTesting
  static final class SummarySectionCallback extends MyFutureCallback {
    private final @NotNull SummarySection mySection;

    @VisibleForTesting
    SummarySectionCallback(@NotNull SummarySection section) {
      mySection = section;
    }

    @Override
    public void onSuccess(@Nullable PhysicalDevice device) {
      assert device != null;

      setText(mySection.myApiLevelLabel, device.getApi());
      setText(mySection.myResolutionLabel, device.getResolution());
      setText(mySection.myDpLabel, device.getDp());
      setText(mySection.myAbiListLabel, device.getAbis());
    }
  }

  @VisibleForTesting
  static final class DeviceSection extends InfoSection {
    @VisibleForTesting final @NotNull JLabel myNameLabel;

    private DeviceSection() {
      super("Device");

      myNameLabel = addNameAndValueLabels("Name");
      setLayout();
    }
  }

  @VisibleForTesting
  static final class DeviceSectionCallback extends MyFutureCallback {
    private final @NotNull DeviceSection mySection;

    @VisibleForTesting
    DeviceSectionCallback(@NotNull DeviceSection section) {
      mySection = section;
    }

    @Override
    public void onSuccess(@Nullable PhysicalDevice device) {
      assert device != null;
      setText(mySection.myNameLabel, device.getName());
    }
  }

  private abstract static class MyFutureCallback implements FutureCallback<PhysicalDevice> {
    @Override
    public void onFailure(@NotNull Throwable throwable) {
      Logger.getInstance(PhysicalDeviceDetailsPanel.class).warn(throwable);
    }
  }

  @VisibleForTesting
  interface NewInfoSectionCallback<S> {
    @NotNull FutureCallback<@NotNull PhysicalDevice> apply(@NotNull S section);
  }

  PhysicalDeviceDetailsPanel(@NotNull PhysicalDevice device, @Nullable Project project) {
    this(device.getName(), new AsyncDetailsBuilder(project, device).buildAsync(), SummarySectionCallback::new, DeviceSectionCallback::new);
  }

  @VisibleForTesting
  PhysicalDeviceDetailsPanel(@NotNull String heading,
                             @NotNull ListenableFuture<@NotNull PhysicalDevice> future,
                             @NotNull NewInfoSectionCallback<@NotNull SummarySection> newSummarySectionCallback,
                             @NotNull NewInfoSectionCallback<@NotNull DeviceSection> newDeviceSectionCallback) {
    super(heading);
    Executor executor = EdtExecutorService.getInstance();

    mySummarySection = new SummarySection();
    Futures.addCallback(future, newSummarySectionCallback.apply(mySummarySection), executor);

    myDeviceSection = new DeviceSection();
    Futures.addCallback(future, newDeviceSectionCallback.apply(myDeviceSection), executor);

    myInfoSections.add(mySummarySection);
    myInfoSections.add(myDeviceSection);

    init();
  }

  @VisibleForTesting
  @NotNull SummarySection getSummarySection() {
    return mySummarySection;
  }

  @VisibleForTesting
  @NotNull DeviceSection getDeviceSection() {
    return myDeviceSection;
  }
}
