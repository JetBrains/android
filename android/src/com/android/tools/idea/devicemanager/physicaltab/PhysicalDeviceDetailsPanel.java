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
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.InfoSection;
import com.android.tools.idea.devicemanager.PairedDevicesPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import java.util.concurrent.Executor;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceDetailsPanel extends DetailsPanel {
  private final boolean myOnline;

  private final @Nullable SummarySection mySummarySection;
  private final @Nullable DeviceSection myDeviceSection;

  @VisibleForTesting
  static final class SummarySection extends InfoSection {
    @VisibleForTesting final @NotNull JLabel myApiLevelLabel;
    private final @NotNull JLabel myPowerLabel;
    @VisibleForTesting final @NotNull JLabel myResolutionLabel;
    @VisibleForTesting final @NotNull JLabel myDpLabel;
    @VisibleForTesting final @NotNull JLabel myAbiListLabel;
    private final @NotNull JLabel myAvailableStorageLabel;

    private SummarySection() {
      super("Summary");

      myApiLevelLabel = addNameAndValueLabels("API level");
      myPowerLabel = addNameAndValueLabels("Power");
      myResolutionLabel = addNameAndValueLabels("Resolution");
      myDpLabel = addNameAndValueLabels("dp");
      myAbiListLabel = addNameAndValueLabels("ABI list");
      myAvailableStorageLabel = addNameAndValueLabels("Available storage");

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

      InfoSection.setText(mySection.myApiLevelLabel, device.getApi());
      InfoSection.setText(mySection.myPowerLabel, device.getPower());
      InfoSection.setText(mySection.myResolutionLabel, device.getResolution());
      InfoSection.setText(mySection.myDpLabel, device.getDp());
      InfoSection.setText(mySection.myAbiListLabel, device.getAbis());
      InfoSection.setText(mySection.myAvailableStorageLabel, device.getStorageDevice());
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
      InfoSection.setText(mySection.myNameLabel, device.getName());
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
    this(device,
         new AsyncDetailsBuilder(project, device).buildAsync(),
         SummarySectionCallback::new,
         DeviceSectionCallback::new,
         WearPairingManager.INSTANCE);
  }

  @VisibleForTesting
  PhysicalDeviceDetailsPanel(@NotNull PhysicalDevice device,
                             @NotNull ListenableFuture<@NotNull PhysicalDevice> future,
                             @NotNull NewInfoSectionCallback<@NotNull SummarySection> newSummarySectionCallback,
                             @NotNull NewInfoSectionCallback<@NotNull DeviceSection> newDeviceSectionCallback,
                             @NotNull WearPairingManager manager) {
    super(device.getName());
    myOnline = device.isOnline();

    if (myOnline) {
      Executor executor = EdtExecutorService.getInstance();

      mySummarySection = new SummarySection();
      Futures.addCallback(future, newSummarySectionCallback.apply(mySummarySection), executor);

      // myDeviceSection = new DeviceSection();
      // Futures.addCallback(future, newDeviceSectionCallback.apply(myDeviceSection), executor);
      myDeviceSection = null;

      myInfoSections.add(mySummarySection);
      InfoSection.newPairedDeviceSection(device, manager).ifPresent(myInfoSections::add);
      // myInfoSections.add(myDeviceSection);

      if (StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get() && device.getType().equals(DeviceType.PHONE)) {
        myPairedDevicesPanel = new PairedDevicesPanel(device.getKey(), this);
      }
    }
    else {
      mySummarySection = null;
      myDeviceSection = null;
    }

    init();
  }

  @Override
  protected void setInfoSectionPanelLayout() {
    if (myOnline) {
      super.setInfoSectionPanelLayout();
      return;
    }

    Component label = new JBLabel("Details unavailable for offline devices");
    GroupLayout layout = new GroupLayout(myInfoSectionPanel);

    Group horizontalGroup = layout.createSequentialGroup()
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addComponent(label)
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);

    Group verticalGroup = layout.createSequentialGroup()
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addComponent(label)
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myInfoSectionPanel.setLayout(layout);
  }

  @VisibleForTesting
  @NotNull SummarySection getSummarySection() {
    assert mySummarySection != null;
    return mySummarySection;
  }

  @VisibleForTesting
  @NotNull DeviceSection getDeviceSection() {
    assert myDeviceSection != null;
    return myDeviceSection;
  }
}
