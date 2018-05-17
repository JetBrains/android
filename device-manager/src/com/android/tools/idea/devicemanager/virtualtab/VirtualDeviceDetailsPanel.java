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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.InfoSection;
import com.android.tools.idea.devicemanager.PairedDevicesPanel;
import com.android.tools.idea.devicemanager.ScreenDiagram;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import java.text.Collator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceDetailsPanel extends DetailsPanel {
  private final @NotNull VirtualDevice myDevice;
  private final @NotNull ListenableFuture<Device> myFuture;
  private final @NotNull Function<SummarySection, FutureCallback<Device>> myNewSummarySectionCallback;

  private @Nullable InfoSection myPropertiesSection;

  @VisibleForTesting
  static final class SummarySection extends InfoSection {
    @VisibleForTesting final @NotNull JLabel myApiLevelLabel;
    @VisibleForTesting final @NotNull JLabel myResolutionLabel;
    @VisibleForTesting final @NotNull JLabel myDpLabel;
    @VisibleForTesting final @NotNull JLabel myAbiListLabel;
    @VisibleForTesting final @NotNull JLabel myAvailableStorageLabel;
    @VisibleForTesting @Nullable JLabel myErrorLabel;
    @VisibleForTesting @Nullable JLabel mySnapshotLabel;

    private SummarySection() {
      super("Summary");

      myApiLevelLabel = addNameAndValueLabels("API level");
      myResolutionLabel = addNameAndValueLabels("Resolution");
      myDpLabel = addNameAndValueLabels("dp");
      myAbiListLabel = addNameAndValueLabels("ABI list");
      myAvailableStorageLabel = addNameAndValueLabels("Available storage");
    }
  }

  VirtualDeviceDetailsPanel(@NotNull VirtualDevice device, @Nullable Project project) {
    this(device, new AsyncVirtualDeviceDetailsBuilder(project, device), VirtualDeviceDetailsPanel::newSummarySectionCallback);
  }

  @VisibleForTesting
  VirtualDeviceDetailsPanel(@NotNull VirtualDevice device,
                            @NotNull AsyncVirtualDeviceDetailsBuilder builder,
                            @NotNull Function<SummarySection, FutureCallback<Device>> newSummarySectionCallback) {
    super(device.getName());

    myDevice = device;
    myFuture = builder.buildAsync();
    myNewSummarySectionCallback = newSummarySectionCallback;

    initScreenDiagram();
    initPropertiesSection();

    InfoSection.newPairedDeviceSection(device, WearPairingManager.getInstance()).ifPresent(myInfoSections::add);

    if (myPropertiesSection != null) {
      myInfoSections.add(myPropertiesSection);
    }

    if (StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get() && device.isPairable()) {
      myPairedDevicesPanel = new PairedDevicesPanel(device.getKey(), this, builder.getProject());
    }

    init();
  }

  @VisibleForTesting
  static @NotNull FutureCallback<Device> newSummarySectionCallback(@NotNull SummarySection section) {
    return new DeviceManagerFutureCallback<>(VirtualDeviceDetailsPanel.class, device -> {
      InfoSection.setText(section.myResolutionLabel, device.getResolution());
      InfoSection.setText(section.myDpLabel, device.getDp());
      InfoSection.setText(section.myAbiListLabel, device.getAbis());
      InfoSection.setText(section.myAvailableStorageLabel, device.getStorageDevice());
    });
  }

  @Override
  protected void initSummarySection() {
    SummarySection summarySection = new SummarySection();
    InfoSection.setText(summarySection.myApiLevelLabel, myDevice.getAndroidVersion().getApiString());

    AvdInfo device = myDevice.getAvdInfo();

    if (!device.getStatus().equals(AvdStatus.OK)) {
      summarySection.myErrorLabel = summarySection.addNameAndValueLabels("Error");
      InfoSection.setText(summarySection.myErrorLabel, device.getErrorMessage());
    }
    else {
      // TODO Ensure this actually displays the snapshot to boot with
      Object snapshot = device.getProperty(AvdManager.AVD_INI_SNAPSHOT_PRESENT);

      if (snapshot != null) {
        summarySection.mySnapshotLabel = summarySection.addNameAndValueLabels("Snapshot");
        InfoSection.setText(summarySection.mySnapshotLabel, snapshot);
      }
    }

    summarySection.setLayout();
    Futures.addCallback(myFuture, myNewSummarySectionCallback.apply(summarySection), EdtExecutorService.getInstance());

    mySummarySection = summarySection;
  }

  private void initScreenDiagram() {
    FutureCallback<Device> callback = new DeviceManagerFutureCallback<>(VirtualDeviceDetailsPanel.class, device -> {
      if (device.getDp() != null) {
        myScreenDiagram = new ScreenDiagram(device);
        setInfoSectionPanelLayout();
      }
    });

    Futures.addCallback(myFuture, callback, EdtExecutorService.getInstance());
  }

  private void initPropertiesSection() {
    AvdInfo device = myDevice.getAvdInfo();

    if (!device.getStatus().equals(AvdStatus.OK)) {
      return;
    }

    Map<String, String> properties = new TreeMap<>(Collator.getInstance());
    properties.putAll(device.getProperties());

    properties.remove(AvdManager.AVD_INI_ABI_TYPE);
    properties.remove(AvdManager.AVD_INI_CPU_ARCH);
    properties.remove(AvdManager.AVD_INI_SKIN_NAME);
    properties.remove(AvdManager.AVD_INI_SKIN_PATH);
    properties.remove(AvdManager.AVD_INI_SDCARD_SIZE);
    properties.remove(AvdManager.AVD_INI_SDCARD_PATH);
    properties.remove(AvdManager.AVD_INI_IMAGES_2);

    if (properties.isEmpty()) {
      return;
    }

    myPropertiesSection = new InfoSection("Properties");

    properties.forEach((name, value) -> InfoSection.setText(myPropertiesSection.addNameAndValueLabels(name), value));
    myPropertiesSection.setLayout();
  }

  @VisibleForTesting
  @NotNull SummarySection getSummarySection() {
    assert mySummarySection != null;
    return (SummarySection)mySummarySection;
  }

  @VisibleForTesting
  @NotNull InfoSection getPropertiesSection() {
    assert myPropertiesSection != null;
    return myPropertiesSection;
  }
}
