/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.deployment.Device.Type;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO Add the thread annotations
final class ConnectedDevicesTask2 implements AsyncSupplier<Collection<ConnectedDevice>> {
  private final @NotNull AndroidDebugBridge myAndroidDebugBridge;

  private ConnectedDevicesTask2(@NotNull AndroidDebugBridge androidDebugBridge) {
    myAndroidDebugBridge = androidDebugBridge;
  }

  @Override
  public @NotNull ListenableFuture<@NotNull Collection<@NotNull ConnectedDevice>> get() {
    var future = myAndroidDebugBridge.getConnectedDevices();

    // noinspection UnstableApiUsage
    return Futures.transformAsync(future, ConnectedDevicesTask2::toList, EdtExecutorService.getInstance());
  }

  private static @NotNull ListenableFuture<@NotNull Collection<@NotNull ConnectedDevice>> toList(@NotNull Collection<@NotNull IDevice> devices) {
    var futures = devices.stream()
      .filter(IDevice::isOnline)
      .map(ConnectedDevicesTask2::buildAsync)
      .toList();

    // noinspection UnstableApiUsage
    return Futures.transform(Futures.successfulAsList(futures), ConnectedDevicesTask2::filterNonNull, EdtExecutorService.getInstance());
  }

  private static @NotNull ListenableFuture<@NotNull ConnectedDevice> buildAsync(@NotNull IDevice device) {
    var future = getNameAsync(device);

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(future).call(() -> build(Futures.getDone(future), device), EdtExecutorService.getInstance());
  }

  private static @NotNull ListenableFuture<@NotNull String> getNameAsync(@NotNull IDevice device) {
    var executor = EdtExecutorService.getInstance();

    if (device.isEmulator()) {
      // noinspection UnstableApiUsage
      return Futures.transform(device.getAvdData(), d -> getName(d.getName(), device.getSerialNumber()), executor);
    }

    var modelFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MODEL);
    var manufacturerFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER);

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(modelFuture, manufacturerFuture)
      .call(() -> DeviceNameProperties.getName(Futures.getDone(modelFuture), Futures.getDone(manufacturerFuture)), executor);
  }

  private static @NotNull String getName(@Nullable String name, @NotNull String serialNumber) {
    if (name == null) {
      return serialNumber;
    }

    if (name.equals("<build>")) {
      return serialNumber;
    }

    return name;
  }

  private static @NotNull ConnectedDevice build(@NotNull String name, @NotNull IDevice device) {
    return new ConnectedDevice.Builder()
      .setName(name)
      // TODO
      .setKey(new VirtualDevicePath("/usr/local/google/home/user/.android/avd/Pixel_6_API_33.avd"))
      .setAndroidDevice(new ConnectedAndroidDevice(device))
      // TODO
      .setType(Type.PHONE)
      // TODO
      .setLaunchCompatibility(LaunchCompatibility.YES)
      .build();
  }

  private static @NotNull Collection<@NotNull ConnectedDevice> filterNonNull(@NotNull Collection<@Nullable ConnectedDevice> devices) {
    // noinspection NullableProblems
    return devices.stream()
      .filter(Objects::nonNull)
      .toList();
  }
}
