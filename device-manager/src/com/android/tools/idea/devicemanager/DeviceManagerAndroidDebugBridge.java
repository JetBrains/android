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
package com.android.tools.idea.devicemanager;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceManagerAndroidDebugBridge {
  private final @NotNull GetDebugBridge myGetDebugBridge;

  public DeviceManagerAndroidDebugBridge() {
    this(DeviceManagerAndroidDebugBridge::getDebugBridge);
  }

  @VisibleForTesting
  DeviceManagerAndroidDebugBridge(@NotNull GetDebugBridge getDebugBridge) {
    myGetDebugBridge = getDebugBridge;
  }

  private static @NotNull ListenableFuture<@NotNull AndroidDebugBridge> getDebugBridge(@Nullable Project project) {
    ListenableFuture<File> future = DeviceManagerFutures.appExecutorServiceSubmit(() -> AndroidSdkUtils.findAdb(project).adbPath);

    // noinspection UnstableApiUsage
    return Futures.transformAsync(future, AdbService.getInstance()::getDebugBridge, AppExecutorUtil.getAppExecutorService());
  }

  @VisibleForTesting
  interface GetDebugBridge {
    @NotNull ListenableFuture<@NotNull AndroidDebugBridge> apply(@Nullable Project project);
  }

  public @NotNull ListenableFuture<@Nullable IDevice> findDevice(@Nullable Project project, @NotNull Key key) {
    // noinspection UnstableApiUsage
    return Futures.transformAsync(getDevices(project), devices -> findDevice(devices, key), AppExecutorUtil.getAppExecutorService());
  }

  private static @NotNull ListenableFuture<@Nullable IDevice> findDevice(@NotNull List<@NotNull IDevice> devices, @NotNull Key key) {
    Iterable<ListenableFuture<AvdData>> futures = devices.stream()
      .map(IDevice::getAvdData)
      .collect(Collectors.toList());

    Executor executor = AppExecutorUtil.getAppExecutorService();

    // noinspection UnstableApiUsage
    return Futures.transform(Futures.successfulAsList(futures), avds -> findDevice(key, avds, devices), executor);
  }

  private static @Nullable IDevice findDevice(@NotNull Key key,
                                              @NotNull List<@Nullable AvdData> avds,
                                              @NotNull List<@NotNull IDevice> devices) {
    Object string = key.toString();

    for (int i = 0, size = avds.size(); i < size; i++) {
      AvdData avd = avds.get(i);

      if (avd == null) {
        continue;
      }

      if (Objects.equals(avd.getPath(), string)) {
        return devices.get(i);
      }
    }

    return null;
  }

  @UiThread
  public @NotNull ListenableFuture<@NotNull List<@NotNull IDevice>> getDevices(@Nullable Project project) {
    Executor executor = AppExecutorUtil.getAppExecutorService();

    // noinspection UnstableApiUsage
    return Futures.transform(myGetDebugBridge.apply(project), DeviceManagerAndroidDebugBridge::getDevices, executor);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull List<@NotNull IDevice> getDevices(@NotNull AndroidDebugBridge bridge) {
    if (!bridge.isConnected()) {
      throw new IllegalArgumentException();
    }

    List<IDevice> devices = Arrays.asList(bridge.getDevices());

    if (bridge.hasInitialDeviceList()) {
      Logger.getInstance(DeviceManagerAndroidDebugBridge.class).info(devices.toString());
    }
    else {
      Logger.getInstance(DeviceManagerAndroidDebugBridge.class).warn("ADB does not have the initial device list");
    }

    return devices;
  }

  @UiThread
  public void addDeviceChangeListener(@NotNull IDeviceChangeListener listener) {
    AndroidDebugBridge.addDeviceChangeListener(listener);
  }

  @UiThread
  public void removeDeviceChangeListener(@NotNull IDeviceChangeListener listener) {
    AndroidDebugBridge.removeDeviceChangeListener(listener);
  }
}
