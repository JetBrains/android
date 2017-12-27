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
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class DevicePickerListModel extends AbstractListModel<DevicePickerEntry> {
  private final ImmutableList<DevicePickerEntry> myEntries;
  private final boolean myShowSerialNumbers;
  private final int myNumConnectedDevices;

  public DevicePickerListModel() {
    this(Collections.emptyList(), Collections.emptyList());
  }

  public DevicePickerListModel(@NotNull List<IDevice> connectedDevices, @NotNull List<AvdInfo> avds) {
    List<AndroidDevice> connected = wrapConnectedDevices(connectedDevices, avds);
    myEntries = createEntries(connected, getLaunchableDevices(avds, getRunningAvds(connectedDevices)));
    myNumConnectedDevices = connected.size();
    myShowSerialNumbers = shouldShowSerials(connected);
  }

  /**
   * Tells the model that the content of one of the list entries has changed.
   *
   * @param entry the entry with the changed content
   */
  public void entryContentChanged(@NotNull DevicePickerEntry entry) {
    int index = findEntry(entry);
    if (index >= 0) {
      fireContentsChanged(entry, index, index);
    }
  }

  private int findEntry(DevicePickerEntry entry) {
    for (int i = 0; i < myEntries.size(); i++) {
      if (myEntries.get(i).equals(entry)) {
        return i;
      }
    }
    return -1;
  }

  @NotNull
  private static List<AndroidDevice> wrapConnectedDevices(@NotNull List<IDevice> connectedDevices, @NotNull List<AvdInfo> avdInfos) {
    List<AndroidDevice> devices = new ArrayList<>(connectedDevices.size());

    for (IDevice device : connectedDevices) {
      devices.add(new ConnectedAndroidDevice(device, avdInfos));
    }
    return devices;
  }

  @NotNull
  private static List<AndroidDevice> getLaunchableDevices(@NotNull List<AvdInfo> avds, @NotNull Set<String> runningAvds) {
    List<AndroidDevice> launchable = new ArrayList<>();

    for (AvdInfo avdInfo : avds) {
      if (!runningAvds.contains(avdInfo.getName())) {
        launchable.add(new LaunchableAndroidDevice(avdInfo));
      }
    }

    return launchable;
  }

  private static Set<String> getRunningAvds(@NotNull List<IDevice> connectedDevices) {
    Set<String> runningAvdNames = new HashSet<>();
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

  @NotNull
  private static ImmutableList<DevicePickerEntry> createEntries(@NotNull List<AndroidDevice> connected,
                                                                @NotNull List<AndroidDevice> launchable) {
    ImmutableList.Builder<DevicePickerEntry> entries = ImmutableList.builder();
    AndroidDeviceComparator comparator = new AndroidDeviceComparator();
    Collections.sort(connected, comparator);
    Collections.sort(launchable, comparator);

    entries.add(DevicePickerEntry.CONNECTED_DEVICES_MARKER);
    if (!connected.isEmpty()) {
      for (AndroidDevice device : connected) {
        entries.add(DevicePickerEntry.create(device));
      }
    }
    else {
      entries.add(DevicePickerEntry.NONE);
    }

    if (!launchable.isEmpty()) {
      entries.add(DevicePickerEntry.LAUNCHABLE_DEVICES_MARKER);

      for (AndroidDevice device : launchable) {
        entries.add(DevicePickerEntry.create(device));
      }
    }
    return entries.build();
  }

  /**
   * Returns the number of connected devices.
   */
  public int getNumberOfConnectedDevices() {
    return myNumConnectedDevices;
  }

  /**
   * Checks whether serial numbers should be displayed for the current set of devices. The serial numbers
   * are displayed only if there are multiple devices with the same manufacturer + model combination.
   */
  private static boolean shouldShowSerials(@NotNull List<AndroidDevice> connectedDevices) {
    int numberOfPhysicalDevices = 0;
    for (AndroidDevice device : connectedDevices) {
      if (!device.isVirtual()) {
        numberOfPhysicalDevices++;
      }
    }

    if (numberOfPhysicalDevices > 1) {
      Set<String> deviceNames = new HashSet<>();

      for (AndroidDevice device : connectedDevices) {
        if (!device.isVirtual()) {
          String name = device.getName();
          if (!deviceNames.add(name)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @Override
  public int getSize() {
    return myEntries.size();
  }

  @Override
  public DevicePickerEntry getElementAt(int index) {
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
