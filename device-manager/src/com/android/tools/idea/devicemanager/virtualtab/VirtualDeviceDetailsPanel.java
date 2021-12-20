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
import com.android.tools.idea.devicemanager.InfoSection;
import com.android.tools.idea.devicemanager.PairedDevicesPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceDetailsPanel extends DetailsPanel {
  private final @NotNull VirtualDevice myDevice;

  private @Nullable SummarySection mySummarySection;
  private @Nullable InfoSection myPropertiesSection;

  @VisibleForTesting
  static final class SummarySection extends InfoSection {
    @VisibleForTesting final @NotNull JLabel myApiLevelLabel;
    @VisibleForTesting final @NotNull JLabel myResolutionLabel;
    @VisibleForTesting final @NotNull JLabel myDpLabel;
    @VisibleForTesting @Nullable JLabel myErrorLabel;
    @VisibleForTesting @Nullable JLabel mySnapshotLabel;

    private SummarySection() {
      super("Summary");

      myApiLevelLabel = addNameAndValueLabels("API level");
      myResolutionLabel = addNameAndValueLabels("Resolution");
      myDpLabel = addNameAndValueLabels("dp");
    }
  }

  VirtualDeviceDetailsPanel(@NotNull VirtualDevice device) {
    this(device, WearPairingManager.INSTANCE, true);
  }

  @VisibleForTesting
  VirtualDeviceDetailsPanel(@NotNull VirtualDevice device, @NotNull WearPairingManager manager, boolean addPairedDevices) {
    super(device.getName());
    myDevice = device;

    initSummarySection();
    initPropertiesSection();

    myInfoSections.add(mySummarySection);
    InfoSection.newPairedDeviceSection(device, manager).ifPresent(myInfoSections::add);

    if (addPairedDevices && StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get()) {
      switch (device.getType()) {
        case PHONE:
        case WEAR_OS:
          myPairedDevicesPanel = new PairedDevicesPanel(myDevice.getKey(), this);
          break;
        default:
          break;
      }
    }

    if (myPropertiesSection != null) {
      myInfoSections.add(myPropertiesSection);
    }

    init();
  }

  private void initSummarySection() {
    mySummarySection = new SummarySection();

    InfoSection.setText(mySummarySection.myApiLevelLabel, myDevice.getApi());
    InfoSection.setText(mySummarySection.myResolutionLabel, myDevice.getResolution());
    InfoSection.setText(mySummarySection.myDpLabel, myDevice.getDp());

    AvdInfo device = myDevice.getAvdInfo();

    if (!device.getStatus().equals(AvdStatus.OK)) {
      mySummarySection.myErrorLabel = mySummarySection.addNameAndValueLabels("Error");
      InfoSection.setText(mySummarySection.myErrorLabel, device.getErrorMessage());
    }
    else {
      Object snapshot = device.getProperty(AvdManager.AVD_INI_SNAPSHOT_PRESENT);

      if (snapshot != null) {
        mySummarySection.mySnapshotLabel = mySummarySection.addNameAndValueLabels("Snapshot");
        InfoSection.setText(mySummarySection.mySnapshotLabel, snapshot);
      }
    }

    mySummarySection.setLayout();
  }

  private void initPropertiesSection() {
    AvdInfo device = myDevice.getAvdInfo();

    if (!device.getStatus().equals(AvdStatus.OK)) {
      return;
    }

    Map<String, String> properties = new HashMap<>(device.getProperties());

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
    return mySummarySection;
  }
}
