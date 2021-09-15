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
import com.android.tools.idea.devicemanager.InfoSection;
import com.android.tools.idea.devicemanager.Resolution;
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
    InfoSection.newPairedDeviceSection(VirtualDevices.build(device)).ifPresent(myInfoSections::add);

    if (myPropertiesSection != null) {
      myInfoSections.add(myPropertiesSection);
    }

    init();
  }

  private void initSummarySection() {
    mySummarySection = new InfoSection("Summary");
    InfoSection.setText(mySummarySection.addNameAndValueLabels("API level"), myDevice.getAndroidVersion().getApiString());

    Resolution resolution = getResolution();

    InfoSection.setText(mySummarySection.addNameAndValueLabels("Resolution"), resolution);
    InfoSection.setText(mySummarySection.addNameAndValueLabels("dp"), getDp(resolution));

    if (!myDevice.getStatus().equals(AvdStatus.OK)) {
      InfoSection.setText(mySummarySection.addNameAndValueLabels("Error"), myDevice.getErrorMessage());
    }
    else {
      Object snapshot = myDevice.getProperty(AvdManager.AVD_INI_SNAPSHOT_PRESENT);

      if (snapshot != null) {
        InfoSection.setText(mySummarySection.addNameAndValueLabels("Snapshot"), snapshot);
      }
    }

    mySummarySection.setLayout();
  }

  private @Nullable Resolution getResolution() {
    String width = myDevice.getProperty("hw.lcd.width");

    if (width == null) {
      return null;
    }

    String height = myDevice.getProperty("hw.lcd.height");

    if (height == null) {
      return null;
    }

    try {
      return new Resolution(Integer.parseInt(width), Integer.parseInt(height));
    }
    catch (NumberFormatException exception) {
      return null;
    }
  }

  private @Nullable Resolution getDp(@Nullable Resolution resolution) {
    String density = myDevice.getProperty("hw.lcd.density");

    if (density == null) {
      return null;
    }

    try {
      return Device.getDp(Integer.parseInt(density), resolution);
    }
    catch (NumberFormatException exception) {
      return null;
    }
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

    properties.forEach((name, value) -> InfoSection.setText(myPropertiesSection.addNameAndValueLabels(name), value));
    myPropertiesSection.setLayout();
  }
}
