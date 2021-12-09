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
import com.android.tools.idea.devicemanager.physicaltab.SerialNumber;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceName;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState;
import org.jetbrains.annotations.NotNull;

final class Pairing {
  private final @NotNull Device myOtherDevice;
  private final @NotNull PairingState myStatus;

  Pairing(@NotNull PairingDevice otherDevice, @NotNull PairingState status) {
    myOtherDevice = toDevice(otherDevice);
    myStatus = status;
  }

  private static @NotNull Device toDevice(@NotNull PairingDevice device) {
    boolean virtual = device.isEmulator();
    DeviceType type = device.isWearDevice() ? DeviceType.WEAR_OS : DeviceType.PHONE;
    AndroidVersion version = new AndroidVersion(device.getApiLevel());

    return new DeviceManagerPairingDevice.Builder()
      .setKey(virtual ? new VirtualDeviceName(device.getDeviceID()) : new SerialNumber(device.getDeviceID()))
      .setType(type)
      .setIcon(virtual ? type.getVirtualIcon() : type.getPhysicalIcon())
      .setName(device.getDisplayName())
      .setOnline(device.isOnline())
      .setTarget(Targets.toString(version))
      .setApi(version.getApiString())
      .build();
  }

  @NotNull Device getOtherDevice() {
    return myOtherDevice;
  }

  @NotNull String getStatus() {
    switch (myStatus) {
      case OFFLINE:
        return "Offline";
      case CONNECTING:
        return "Connecting";
      case CONNECTED:
        return "Connected";
      case PAIRING_FAILED:
        return "Error pairing";
      default:
        throw new AssertionError(myStatus);
    }
  }
}
