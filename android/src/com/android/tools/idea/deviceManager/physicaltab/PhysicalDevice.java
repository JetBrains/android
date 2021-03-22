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
package com.android.tools.idea.deviceManager.physicaltab;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevice {
  private final @NotNull Object mySerialNumber;
  private final boolean myConnected;

  private PhysicalDevice(@NotNull String serialNumber, boolean connected) {
    mySerialNumber = serialNumber;
    myConnected = connected;
  }

  static @NotNull PhysicalDevice newConnectedDevice(@NotNull String serialNumber) {
    return new PhysicalDevice(serialNumber, true);
  }

  static @NotNull PhysicalDevice newDisconnectedDevice(@NotNull String serialNumber) {
    return new PhysicalDevice(serialNumber, false);
  }

  @NotNull Object getSerialNumber() {
    return mySerialNumber;
  }

  boolean isConnected() {
    return myConnected;
  }

  @NotNull String toDebugString() {
    String separator = System.lineSeparator();

    return "serialNumber = " + mySerialNumber + separator
           + "connected = " + myConnected + separator;
  }

  @Override
  public int hashCode() {
    return 31 * mySerialNumber.hashCode() + Boolean.hashCode(myConnected);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    PhysicalDevice device = (PhysicalDevice)object;
    return mySerialNumber.equals(device.mySerialNumber) && myConnected == device.myConnected;
  }
}