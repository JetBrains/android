/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager.DeviceFilter;
import com.android.tools.idea.avdmanager.DeviceManagerConnection;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

final class DeviceSupplier {
  @NotNull
  private final Supplier<DeviceManagerConnection> myGetDefaultDeviceManagerConnection;

  DeviceSupplier() {
    this(DeviceManagerConnection::getDefaultDeviceManagerConnection);
  }

  @VisibleForTesting
  DeviceSupplier(@NotNull Supplier<DeviceManagerConnection> getDefaultDeviceManagerConnection) {
    myGetDefaultDeviceManagerConnection = getDefaultDeviceManagerConnection;
  }

  @NotNull
  Collection<Device> get() {
    var connection = myGetDefaultDeviceManagerConnection.get();
    var systemImageFilter = EnumSet.of(DeviceFilter.SYSTEM_IMAGES);

    var systemImageDevices = connection.getDevices(systemImageFilter).stream()
      .filter(Predicate.not(DeviceSupplier::isDeprecatedWearOsDevice));

    return Stream.concat(connection.getDevices(EnumSet.complementOf(systemImageFilter)).stream(), systemImageDevices)
      .collect(Collectors.toMap(Key::new, device -> device, (oldDevice, newDevice) -> newDevice))
      .values();
  }

  private static boolean isDeprecatedWearOsDevice(@NotNull Device device) {
    return !device.getId().startsWith("wearos") && Objects.equals(device.getTagId(), "android-wear");
  }

  private record Key(@NotNull String id, @NotNull String manufacturer) {
    private Key(@NotNull Device device) {
      this(device.getId(), device.getManufacturer());
    }
  }
}
