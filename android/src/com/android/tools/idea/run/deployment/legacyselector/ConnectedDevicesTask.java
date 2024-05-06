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
package com.android.tools.idea.run.deployment.legacyselector;

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
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO Add the thread annotations
public final class ConnectedDevicesTask implements AsyncSupplier<Collection<ConnectedDevice>> {
  private final @NotNull AndroidDebugBridge myAndroidDebugBridge;

  @NotNull
  private final ProvisionerHelper myHelper;

  private final @NotNull Supplier<LaunchCompatibilityChecker> myLaunchCompatibilityCheckerSupplier;

  @NotNull
  private final Function<IDevice, AndroidDevice> myNewConnectedAndroidDevice;

  public ConnectedDevicesTask(@NotNull AndroidDebugBridge androidDebugBridge,
                       @NotNull ProvisionerHelper helper,
                       @NotNull Supplier<LaunchCompatibilityChecker> launchCompatibilityCheckerSupplier) {
    this(androidDebugBridge, helper, launchCompatibilityCheckerSupplier, ConnectedAndroidDevice::new);
  }

  @VisibleForTesting
  ConnectedDevicesTask(@NotNull AndroidDebugBridge androidDebugBridge,
                       @NotNull ProvisionerHelper helper,
                       @NotNull Supplier<LaunchCompatibilityChecker> launchCompatibilityCheckerSupplier,
                       @NotNull Function<IDevice, AndroidDevice> newConnectedAndroidDevice) {
    myAndroidDebugBridge = androidDebugBridge;
    myHelper = helper;
    myLaunchCompatibilityCheckerSupplier = launchCompatibilityCheckerSupplier;
    myNewConnectedAndroidDevice = newConnectedAndroidDevice;
  }

  @NotNull
  @Override
  public ListenableFuture<Collection<ConnectedDevice>> get() {
    // noinspection UnstableApiUsage
    return Futures.transformAsync(myAndroidDebugBridge.getConnectedDevices(), this::toList, AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private ListenableFuture<Collection<ConnectedDevice>> toList(@NotNull Collection<IDevice> devices) {
    var futures = devices.stream()
      .filter(IDevice::isOnline)
      .map(this::buildAsync)
      .toList();

    // noinspection UnstableApiUsage
    return Futures.transform(Futures.successfulAsList(futures),
                             ConnectedDevicesTask::filterNonNull,
                             AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private ListenableFuture<ConnectedDevice> buildAsync(@NotNull IDevice device) {
    var androidDevice = myNewConnectedAndroidDevice.apply(device);

    var keyFuture = getKeyAsync(device);
    var iconFuture = myHelper.getIcon(device);
    var compatibilityFuture = getLaunchCompatibilityAsync(androidDevice);
    var nameFuture = getNameAsync(device);

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(keyFuture, iconFuture, compatibilityFuture, nameFuture)
      .call(() -> build(keyFuture, iconFuture, compatibilityFuture, nameFuture, androidDevice), AppExecutorUtil.getAppExecutorService());
  }

  private static @NotNull ListenableFuture<String> getNameAsync(@NotNull IDevice device) {
    var executor = AppExecutorUtil.getAppExecutorService();

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
    return Futures.transform(device.getAvdData(), d -> getKey(d, serialNumber), AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private static Key getKey(@Nullable AvdData device, @NotNull String serialNumber) {
    if (device == null) {
      return new SerialNumber(serialNumber);
    }

    var path = device.getNioPath();

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
    LaunchCompatibilityChecker checker = myLaunchCompatibilityCheckerSupplier.get();
    if (checker == null) {
      return Futures.immediateFuture(Optional.empty());
    }

    return Futures.submit(() -> Optional.of(checker.validate(device)), AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private static ConnectedDevice build(@NotNull Future<Key> keyFuture,
                                       @NotNull Future<Optional<Icon>> iconFuture,
                                       @NotNull Future<Optional<LaunchCompatibility>> launchCompatibilityFuture,
                                       @NotNull Future<String> nameFuture,
                                       @NotNull AndroidDevice androidDevice) throws ExecutionException {
    var type = Tasks.getTypeFromAndroidDevice(androidDevice);

    var builder = new ConnectedDevice.Builder()
      .setKey(Futures.getDone(keyFuture))
      .setIcon(Futures.getDone(iconFuture).orElse(androidDevice.isVirtual() ? type.getVirtualIcon() : type.getPhysicalIcon()))
      .setType(type)
      .setName(Futures.getDone(nameFuture))
      .setAndroidDevice(androidDevice);

    Futures.getDone(launchCompatibilityFuture).ifPresent(builder::setLaunchCompatibility);
    return builder.build();
  }

  private static @NotNull Collection<ConnectedDevice> filterNonNull(@NotNull Collection<ConnectedDevice> devices) {
    return devices.stream()
      .filter(Objects::nonNull)
      .toList();
  }
}
