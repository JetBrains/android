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

import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO Add the thread annotations
final class ConnectedDevicesTask2 implements AsyncSupplier<Collection<ConnectedDevice>> {
  private final @NotNull AndroidDebugBridge myAndroidDebugBridge;
  private final @Nullable LaunchCompatibilityChecker myLaunchCompatibilityChecker;

  @NotNull
  private final Function<IDevice, AndroidDevice> myNewConnectedAndroidDevice;

  ConnectedDevicesTask2(@NotNull AndroidDebugBridge androidDebugBridge, @Nullable LaunchCompatibilityChecker launchCompatibilityChecker) {
    this(androidDebugBridge, launchCompatibilityChecker, ConnectedAndroidDevice::new);
  }

  @VisibleForTesting
  ConnectedDevicesTask2(@NotNull AndroidDebugBridge androidDebugBridge,
                        @Nullable LaunchCompatibilityChecker launchCompatibilityChecker,
                        @NotNull Function<IDevice, AndroidDevice> newConnectedAndroidDevice) {
    myAndroidDebugBridge = androidDebugBridge;
    myLaunchCompatibilityChecker = launchCompatibilityChecker;
    myNewConnectedAndroidDevice = newConnectedAndroidDevice;
  }

  @NotNull
  @Override
  public ListenableFuture<Collection<ConnectedDevice>> get() {
    // noinspection UnstableApiUsage
    return Futures.transformAsync(myAndroidDebugBridge.getConnectedDevices(), this::toList, EdtExecutorService.getInstance());
  }

  @NotNull
  private ListenableFuture<Collection<ConnectedDevice>> toList(@NotNull Collection<IDevice> devices) {
    var futures = devices.stream()
      .filter(IDevice::isOnline)
      .map(this::buildAsync)
      .toList();

    // noinspection UnstableApiUsage
    return Futures.transform(Futures.successfulAsList(futures), ConnectedDevicesTask2::filterNonNull, EdtExecutorService.getInstance());
  }

  @NotNull
  private ListenableFuture<ConnectedDevice> buildAsync(@NotNull IDevice device) {
    var androidDevice = myNewConnectedAndroidDevice.apply(device);

    var nameFuture = getNameAsync(device);
    var keyFuture = getKeyAsync(device);
    var compatibilityFuture = getLaunchCompatibilityAsync(androidDevice);

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(nameFuture, keyFuture, compatibilityFuture)
      .call(() -> build(androidDevice, nameFuture, keyFuture, compatibilityFuture), EdtExecutorService.getInstance());
  }

  private static @NotNull ListenableFuture<String> getNameAsync(@NotNull IDevice device) {
    var executor = EdtExecutorService.getInstance();

    if (device.isEmulator()) {
      // noinspection UnstableApiUsage
      return Futures.transform(device.getAvdData(), d -> getName(d, device.getSerialNumber()), executor);
    }

    var modelFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MODEL);
    var manufacturerFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER);

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(modelFuture, manufacturerFuture)
      .call(() -> DeviceNameProperties.getName(Futures.getDone(modelFuture), Futures.getDone(manufacturerFuture)), executor);
  }

  @NotNull
  private static String getName(@Nullable AvdData device, @NotNull String serialNumber) {
    if (device == null) {
      return serialNumber;
    }

    var name = device.getName();

    if (name == null) {
      return serialNumber;
    }

    if (name.equals("<build>")) {
      return serialNumber;
    }

    return name;
  }

  private static @NotNull ListenableFuture<Key> getKeyAsync(@NotNull IDevice device) {
    var serialNumber = device.getSerialNumber();

    if (!device.isEmulator()) {
      return Futures.immediateFuture(new SerialNumber(serialNumber));
    }

    // noinspection UnstableApiUsage
    return Futures.transform(device.getAvdData(), d -> getKey(d, serialNumber), EdtExecutorService.getInstance());
  }

  @NotNull
  private static Key getKey(@Nullable AvdData device, @NotNull String serialNumber) {
    if (device == null) {
      return new SerialNumber(serialNumber);
    }

    var path = device.getPath();

    if (path != null) {
      return new VirtualDevicePath(path);
    }

    var name = device.getName();

    if (name == null) {
      return new SerialNumber(serialNumber);
    }

    if (name.equals("<build>")) {
      return new SerialNumber(serialNumber);
    }

    return new VirtualDeviceName(name);
  }

  @NotNull
  private ListenableFuture<Optional<LaunchCompatibility>> getLaunchCompatibilityAsync(@NotNull AndroidDevice device) {
    if (myLaunchCompatibilityChecker == null) {
      return Futures.immediateFuture(Optional.empty());
    }

    return Futures.submit(() -> Optional.of(myLaunchCompatibilityChecker.validate(device)), AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private static ConnectedDevice build(@NotNull AndroidDevice device,
                                       @NotNull Future<String> nameFuture,
                                       @NotNull Future<Key> keyFuture,
                                       @NotNull Future<Optional<LaunchCompatibility>> compatibilityFuture) throws ExecutionException {
    var builder = new ConnectedDevice.Builder()
      .setName(Futures.getDone(nameFuture))
      .setKey(Futures.getDone(keyFuture))
      .setAndroidDevice(device)
      .setType(Tasks.getTypeFromAndroidDevice(device));

    Futures.getDone(compatibilityFuture).ifPresent(builder::setLaunchCompatibility);
    return builder.build();
  }

  private static @NotNull Collection<ConnectedDevice> filterNonNull(@NotNull Collection<ConnectedDevice> devices) {
    return devices.stream()
      .filter(Objects::nonNull)
      .toList();
  }
}
