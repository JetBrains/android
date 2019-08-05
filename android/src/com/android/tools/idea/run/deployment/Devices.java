/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Devices {
  private Devices() {
  }

  @NotNull
  static String getText(@NotNull Device device, @NotNull Collection<Device> devices) {
    return getText(device, devices, null);
  }

  @NotNull
  static String getText(@NotNull Device device, @NotNull Collection<Device> devices, @Nullable Snapshot snapshot) {
    String key = device.getKey();
    String name = device.getName();

    boolean match = devices.stream()
      .filter(d -> !d.getKey().equals(key))
      .map(Device::getName)
      .anyMatch(name::equals);

    StringBuilder builder = new StringBuilder(name);

    if (match) {
      builder
        .append(" [")
        .append(key)
        .append(']');
    }

    if (snapshot != null) {
      builder
        .append(" - ")
        .append(snapshot);
    }

    String reason = device.getValidityReason();

    if (reason != null) {
      builder
        .append(" (")
        .append(reason)
        .append(')');
    }

    return builder.toString();
  }
}
