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
    Comparator.comparing(device -> device.myConnectionTime, Comparator.nullsLast(Comparator.reverseOrder()));

  private final @NotNull String mySerialNumber;
  private final @Nullable Instant myConnectionTime;

  PhysicalDevice(@NotNull String serialNumber) {
    this(serialNumber, null);
  }

  PhysicalDevice(@NotNull String serialNumber, @Nullable Instant connectionTime) {
    mySerialNumber = serialNumber;
    myConnectionTime = connectionTime;
  }

  @NotNull String getSerialNumber() {
    return mySerialNumber;
  }

  boolean isConnected() {
    return myConnectionTime != null;
  }

  @NotNull String toDebugString() {
    String separator = System.lineSeparator();

    return "serialNumber = " + mySerialNumber + separator
           + "connectionTime = " + myConnectionTime + separator;
  }

  @Override
  public int hashCode() {
    return 31 * mySerialNumber.hashCode() + Objects.hashCode(myConnectionTime);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    PhysicalDevice device = (PhysicalDevice)object;
    return mySerialNumber.equals(device.mySerialNumber) && Objects.equals(myConnectionTime, device.myConnectionTime);
  }

  @Override
  public int compareTo(@NotNull PhysicalDevice device) {
    return COMPARATOR.compare(this, device);
  }
}
