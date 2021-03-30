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

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevice implements Comparable<@NotNull PhysicalDevice> {
  private static final @NotNull Comparator<@NotNull PhysicalDevice> COMPARATOR =
    Comparator.comparing(PhysicalDevice::isConnected, Comparator.reverseOrder())
      .thenComparing(PhysicalDevice::getLastConnectionTime, Comparator.nullsLast(Comparator.reverseOrder()));

  private final @NotNull String mySerialNumber;
  private final boolean myConnected;
  private final @Nullable Instant myLastConnectionTime;

  private PhysicalDevice(@NotNull String serialNumber, boolean connected, @Nullable Instant lastConnectionTime) {
    mySerialNumber = serialNumber;
    myConnected = connected;
    myLastConnectionTime = lastConnectionTime;
  }

  static @NotNull PhysicalDevice newConnectedDevice(@NotNull String serialNumber, @NotNull Instant lastConnectionTime) {
    return new PhysicalDevice(serialNumber, true, lastConnectionTime);
  }

  static @NotNull PhysicalDevice newDisconnectedDevice(@NotNull String serialNumber, @Nullable Instant lastConnectionTime) {
    return new PhysicalDevice(serialNumber, false, lastConnectionTime);
  }

  @NotNull String getSerialNumber() {
    return mySerialNumber;
  }

  boolean isConnected() {
    return myConnected;
  }

  @Nullable Instant getLastConnectionTime() {
    return myLastConnectionTime;
  }

  @NotNull String toDebugString() {
    String separator = System.lineSeparator();

    return "serialNumber = " + mySerialNumber + separator
           + "connected = " + myConnected + separator
           + "lastConnectionTime = " + myLastConnectionTime + separator;
  }

  @Override
  public int hashCode() {
    int hashCode = mySerialNumber.hashCode();

    hashCode = 31 * hashCode + Boolean.hashCode(myConnected);
    hashCode = 31 * hashCode + Objects.hashCode(myLastConnectionTime);

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    PhysicalDevice device = (PhysicalDevice)object;

    return mySerialNumber.equals(device.mySerialNumber) &&
           myConnected == device.myConnected &&
           Objects.equals(myLastConnectionTime, device.myLastConnectionTime);
  }

  @Override
  public int compareTo(@NotNull PhysicalDevice device) {
    return COMPARATOR.compare(this, device);
  }
}
