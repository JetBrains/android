/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DevicePickerListModel extends AbstractListModel {
  private final List<DevicePickerEntry> myEntries = Lists.newArrayList();
  private boolean myShowSerialNumbers;

  public void reset(@NotNull List<IDevice> connectedDevices, @NotNull List<AvdInfo> avds) {
    clear();

    List<AndroidDevice> connected = wrapConnectedDevices(connectedDevices, avds);
    addEntries(connected, getLaunchableDevices(avds, getRunningAvds(connectedDevices)));

    myShowSerialNumbers = shouldShowSerials(connected);
  }

  private static List<AndroidDevice> wrapConnectedDevices(@NotNull List<IDevice> connectedDevices, @NotNull List<AvdInfo> avdInfos) {
    List<AndroidDevice> devices = Lists.newArrayList();

    for (IDevice device : connectedDevices) {
      devices.add(new ConnectedAndroidDevice(device, avdInfos));
    }
    return devices;
  }

  @NotNull
  private static List<AndroidDevice> getLaunchableDevices(@NotNull List<AvdInfo> avds, @NotNull Set<String> runningAvds) {
    List<AndroidDevice> launchable = Lists.newArrayList();

    for (AvdInfo avdInfo : avds) {
      if (!runningAvds.contains(avdInfo.getName())) {
        launchable.add(new LaunchableAndroidDevice(avdInfo));
      }
    }

    return launchable;
  }

  private static Set<String> getRunningAvds(@NotNull List<IDevice> connectedDevices) {
    Set<String> runningAvdNames = Sets.newHashSet();
    for (IDevice device : connectedDevices) {
      if (device.isEmulator()) {
        String avdName = device.getAvdName();
        if (avdName != null) {
          runningAvdNames.add(avdName);
        }
      }
    }
    return runningAvdNames;
  }

  private void addEntries(@NotNull List<AndroidDevice> connected, @NotNull List<AndroidDevice> launchable) {
    AndroidDeviceComparator comparator = new AndroidDeviceComparator();
    Collections.sort(connected, comparator);
    Collections.sort(launchable, comparator);

    myEntries.add(DevicePickerEntry.CONNECTED_DEVICES_MARKER);
    if (!connected.isEmpty()) {
      for (AndroidDevice device : connected) {
        myEntries.add(DevicePickerEntry.create(device));
      }
    }
    else {
      myEntries.add(DevicePickerEntry.NONE);
    }

    if (!launchable.isEmpty()) {
      myEntries.add(DevicePickerEntry.LAUNCHABLE_DEVICES_MARKER);

      for (AndroidDevice device : launchable) {
        myEntries.add(DevicePickerEntry.create(device));
      }
    }

    int size = myEntries.size();
    if (size > 0) {
      fireIntervalAdded(this, 0, size - 1);
    }
  }

  private void clear() {
    int oldSize = getSize();
    myEntries.clear();
    if (oldSize > 0) {
      fireIntervalRemoved(this, 0, oldSize - 1);
    }
  }

  // Returns whether serial numbers should be displayed for the current set of devices.
  // We only display serial numbers if there are multiple devices with the same manufacturer + model combination
  private static boolean shouldShowSerials(@NotNull List<AndroidDevice> connectedDevices) {
    Set<String> myModelNames = Sets.newHashSet();

    for (AndroidDevice device : connectedDevices) {
      if (device.isVirtual()) {
        continue;
      }

      String name = device.getName();
      if (myModelNames.contains(name)) {
        return true;
      }

      myModelNames.add(name);
    }

    return false;
  }

  @Override
  public int getSize() {
    return myEntries.size();
  }

  @Override
  public Object getElementAt(int index) {
    return myEntries.get(index);
  }

  @NotNull
  public List<DevicePickerEntry> getItems() {
    return myEntries;
  }

  public boolean shouldShowSerialNumbers() {
    return myShowSerialNumbers;
  }
}
