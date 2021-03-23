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

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;

final class PhysicalDevices {
  private PhysicalDevices() {
  }

  static int indexOf(@NotNull List<@NotNull PhysicalDevice> devices, @NotNull PhysicalDevice device) {
    Object serialNumber = device.getSerialNumber();

    OptionalInt optionalIndex = IntStream.range(0, devices.size())
      .filter(index -> devices.get(index).getSerialNumber().equals(serialNumber))
      .findFirst();

    return optionalIndex.orElse(-1);
  }
}
