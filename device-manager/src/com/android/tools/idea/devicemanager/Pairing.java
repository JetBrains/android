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
package com.android.tools.idea.devicemanager;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevicePath;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

final class Pairing {
  private final @NotNull PhoneWearPair myPair;
  private final @NotNull DeviceManagerPairingDevice myOtherDevice;

  Pairing(@NotNull PhoneWearPair pair, @NotNull Key key) {
    myPair = pair;
    myOtherDevice = toDeviceManagerPairingDevice(pair.getPeerDevice(key.toString()));
  }

  private @NotNull DeviceManagerPairingDevice toDeviceManagerPairingDevice(@NotNull PairingDevice device) {
    boolean virtual = device.isEmulator();
    DeviceType type = device.isWearDevice() ? DeviceType.WEAR_OS : DeviceType.PHONE;
    AndroidVersion version = new AndroidVersion(device.getApiLevel());

    return new DeviceManagerPairingDevice.Builder()
      .setKey(virtual ? new VirtualDevicePath(device.getDeviceID()) : new SerialNumber(device.getDeviceID()))
      .setType(type)
      .setIcon(virtual ? type.getVirtualIcon() : type.getPhysicalIcon())
      .setName(device.getDisplayName())
      .setOnline(device.isOnline())
      .setTarget(Targets.toString(version))
      .setStatus(newStatus(device))
      .setAndroidVersion(version)
      .build();
  }

  private @NotNull Status newStatus(@NotNull PairingDevice device) {
    if (!device.isEmulator() && myPair.getPairingStatus().equals(PairingState.OFFLINE)) {
      return new Status("Unavailable", AllIcons.General.ShowInfos, "<html>Connect device to begin<br>" +
                                                                   "communication between devices</html>");
    }

    return new Status(getPairingStatus());
  }

  private @NotNull String getPairingStatus() {
    switch (myPair.getPairingStatus()) {
      case UNKNOWN:
        return "Unknown";
      case OFFLINE:
        return "Offline";
      case CONNECTING:
        return "Connecting";
      case CONNECTED:
        return "Connected";
      case PAIRING_FAILED:
        return "Error pairing";
      default:
        throw new AssertionError(myPair.getPairingStatus());
    }
  }

  @NotNull PhoneWearPair getPair() {
    return myPair;
  }

  @NotNull DeviceManagerPairingDevice getOtherDevice() {
    return myOtherDevice;
  }
}
