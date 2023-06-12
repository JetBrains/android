/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * A deployment target for an app. A user actually selects these (and not devices) with the drop down or Select Multiple Devices dialog. The
 * subclasses for virtual devices boot them differently.
 */
abstract class Target {
  private final @NotNull Key myDeviceKey;

  Target(@NotNull Key deviceKey) {
    myDeviceKey = deviceKey;
  }

  static @NotNull List<Device> filterDevices(@NotNull Set<Target> targets, @NotNull List<Device> devices) {
    Set<Key> keys = targets.stream()
      .map(Target::getDeviceKey)
      .collect(Collectors.toSet());

    return devices.stream()
      .filter(device -> keys.contains(device.getKey()))
      .collect(Collectors.toList());
  }

  final @NotNull Key getDeviceKey() {
    return myDeviceKey;
  }

  boolean matches(@NotNull Device device) {
    return device.getKey().equals(myDeviceKey);
  }

  /**
   * @return the text for this target. It's used for the items in a virtual device's submenu and in the drop down button when a user selects
   * a target.
   */
  abstract @NotNull String getText(@NotNull Device device);

  abstract void boot(@NotNull VirtualDevice device, @NotNull Project project);
}
