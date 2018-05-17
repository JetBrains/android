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
package com.android.tools.idea.run.deployment;

import static com.android.tools.idea.run.deployment.Tasks.getTypeFromAndroidDevice;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ConnectedDevicesTask implements AsyncSupplier<List<ConnectedDevice>> {
  @NotNull
  private final AndroidDebugBridge myAndroidDebugBridge;

  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  @NotNull
  private final Executor myExecutor;

  @NotNull
  private final Function<IDevice, AndroidDevice> myAndroidDeviceFactory;

  ConnectedDevicesTask(@NotNull AndroidDebugBridge androidDebugBridge, @Nullable LaunchCompatibilityChecker checker) {
    this(androidDebugBridge, checker, AppExecutorUtil.getAppExecutorService(), device -> new ConnectedAndroidDevice(device));
  }

  @VisibleForTesting
  ConnectedDevicesTask(@NotNull AndroidDebugBridge androidDebugBridge,
                       @Nullable LaunchCompatibilityChecker checker,
                       @NotNull Executor executor,
                       @NotNull Function<IDevice, AndroidDevice> androidDeviceFactory) {
    myAndroidDebugBridge = androidDebugBridge;
    myChecker = checker;
    myExecutor = executor;
    myAndroidDeviceFactory = androidDeviceFactory;
  }

  @NotNull
  @Override
  public ListenableFuture<List<ConnectedDevice>> get() {
    // noinspection UnstableApiUsage
    return Futures.transform(myAndroidDebugBridge.getConnectedDevices(), this::newConnectedDevices, myExecutor);
  }

  private @NotNull List<ConnectedDevice> newConnectedDevices(@NotNull Collection<IDevice> devices) {
    return devices.stream()
      .filter(IDevice::isOnline)
      .map(this::newConnectedDevice)
      .collect(Collectors.toList());
  }

  private @NotNull ConnectedDevice newConnectedDevice(@NotNull IDevice ddmlibDevice) {
    AndroidDevice androidDevice = myAndroidDeviceFactory.apply(ddmlibDevice);

    ConnectedDevice.Builder builder = new ConnectedDevice.Builder()
      .setName(composeDeviceName(ddmlibDevice))
      .setKey(newKey(ddmlibDevice))
      .setAndroidDevice(androidDevice)
      .setType(getTypeFromAndroidDevice(androidDevice));

    if (myChecker == null) {
      return builder.build();
    }

    return builder
      .setLaunchCompatibility(myChecker.validate(androidDevice))
      .build();
  }

  @NotNull
  private static String composeDeviceName(@NotNull IDevice ddmlibDevice) {
    if (ddmlibDevice.isEmulator()) {
      String avdName = ddmlibDevice.getAvdName();
      // The AVD name "<build>" is produced in case of a custom system image.
      if (avdName != null && !avdName.equals("<build>")) {
        return avdName;
      }
    }
    return ddmlibDevice.getSerialNumber();
  }

  private static @NotNull Key newKey(@NotNull IDevice device) {
    if (!device.isEmulator()) {
      return new SerialNumber(device.getSerialNumber());
    }

    String path = device.getAvdPath();

    if (path != null) {
      return new VirtualDevicePath(path);
    }

    String name = device.getAvdName();

    if (name != null && !name.equals("<build>")) {
      return new VirtualDeviceName(name);
    }

    // Either the virtual device name is null or the developer built their own system image. Neither names will work as virtual device keys.
    // Fall back to the serial number.
    return new SerialNumber(device.getSerialNumber());
  }
}
