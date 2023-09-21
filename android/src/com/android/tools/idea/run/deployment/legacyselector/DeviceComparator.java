/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import java.util.Comparator;
import org.jetbrains.annotations.NotNull;

final class DeviceComparator implements Comparator<Device> {
  private static final Comparator<Device> COMPARATOR =
    Comparator.comparing((@NotNull Device device) -> device.launchCompatibility().getState())
      .thenComparing(Device::connectionTime, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(DeviceComparator::getType)
      .thenComparing(Device::name);

  @NotNull
  private static Type getType(@NotNull Device device) {
    if (device instanceof VirtualDevice) {
      return Type.VIRTUAL_DEVICE;
    }

    if (device instanceof PhysicalDevice) {
      return Type.PHYSICAL_DEVICE;
    }

    throw new AssertionError(device);
  }

  private enum Type {VIRTUAL_DEVICE, PHYSICAL_DEVICE}

  @Override
  public int compare(@NotNull Device device1, @NotNull Device device2) {
    return COMPARATOR.compare(device1, device2);
  }
}
