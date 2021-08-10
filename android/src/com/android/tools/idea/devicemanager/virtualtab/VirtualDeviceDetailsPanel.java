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
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceDetailsPanel extends DetailsPanel {
  // TODO Use VirtualDevice instead
  private final @NotNull AvdInfo myDevice;

  private @Nullable InfoSection mySummarySection;
  private @Nullable InfoSection myPropertiesSection;

  VirtualDeviceDetailsPanel(@NotNull AvdInfo device) {
    super(device.getDisplayName());
    myDevice = device;

    initSummarySection();
    initPropertiesSection();

    myInfoSections.add(mySummarySection);

    if (myPropertiesSection != null) {
      myInfoSections.add(myPropertiesSection);
    }

    init();
  }

  private void initSummarySection() {
    mySummarySection = new InfoSection("Summary");

    setText(mySummarySection.addNameAndValueLabels("Name:"), myDevice.getName());
    setText(mySummarySection.addNameAndValueLabels("CPU/ABI:"), AvdInfo.getPrettyAbiType(myDevice));
    setText(mySummarySection.addNameAndValueLabels("Path:"), myDevice.getDataFolderPath());

    if (!myDevice.getStatus().equals(AvdStatus.OK)) {
      setText(mySummarySection.addNameAndValueLabels("Error:"), myDevice.getErrorMessage());
    }
    else {
      Object target = myDevice.getTag() + " (API level " + myDevice.getAndroidVersion().getApiString() + ')';
      setText(mySummarySection.addNameAndValueLabels("Target:"), target);

      Object skin = myDevice.getProperty(AvdManager.AVD_INI_SKIN_NAME);

      if (skin != null) {
        setText(mySummarySection.addNameAndValueLabels("Skin:"), skin);
      }

      Object sdCard = myDevice.getProperty(AvdManager.AVD_INI_SDCARD_SIZE);

      if (sdCard == null) {
        sdCard = myDevice.getProperty(AvdManager.AVD_INI_SDCARD_PATH);
      }

      if (sdCard != null) {
        setText(mySummarySection.addNameAndValueLabels("SD Card:"), sdCard);
      }

      Object snapshot = myDevice.getProperty(AvdManager.AVD_INI_SNAPSHOT_PRESENT);

      if (snapshot != null) {
        setText(mySummarySection.addNameAndValueLabels("Snapshot:"), snapshot);
      }
    }

    mySummarySection.setLayout();
  }

  private void initPropertiesSection() {
    if (!myDevice.getStatus().equals(AvdStatus.OK)) {
      return;
    }

    Map<String, String> properties = new HashMap<>(myDevice.getProperties());

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

    properties.forEach((name, value) -> setText(myPropertiesSection.addNameAndValueLabels(name + ": "), value));
    myPropertiesSection.setLayout();
  }
}
