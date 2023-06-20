/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.Key;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

final class VirtualDeviceTableWatcherListener implements VirtualDeviceWatcherListener {
  private final @NotNull VirtualDeviceTable myTable;

  VirtualDeviceTableWatcherListener(@NotNull VirtualDeviceTable table) {
    myTable = table;
  }

  @Override
  public void virtualDevicesChanged(@NotNull VirtualDeviceWatcherEvent event) {
    VirtualDeviceTableModel model = myTable.getModel();
    Map<Key, VirtualDevice> keyToOldDeviceMap = model.getDevices().stream().collect(Collectors.toMap(Device::getKey, device -> device));

    for (AvdInfo newAvd : event.getAvds()) {
      Key newKey = new VirtualDevicePath(newAvd.getId());
      VirtualDevice oldDevice = keyToOldDeviceMap.remove(newKey);

      if (oldDevice == null) {
        // If this AVD doesn't exist in the map of old devices, then this is a new AVD
        myTable.addDevice(newKey);
      }
      else if (!oldDevice.getAvdInfo().equals(newAvd)) {
        // If the AVD ID is the same but the AvdInfos are not equal, the AVD has changed
        myTable.reloadDevice(newKey);
      }
    }

    // Any AVDs left over in the map are AVDs that have been deleted
    keyToOldDeviceMap.keySet().forEach(model::remove);
  }
}
