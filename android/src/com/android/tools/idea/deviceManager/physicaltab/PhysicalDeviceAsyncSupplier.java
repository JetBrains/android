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

import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellEnabledDevice;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceAsyncSupplier {
  private final @Nullable Project myProject;
  private final @NotNull ListeningExecutorService myService;
  private final @NotNull Function<@Nullable Project, @NotNull Path> myGetAdb;
  private final @NotNull AsyncFunction<@NotNull Path, @Nullable AndroidDebugBridge> myGetDebugBridge;
  private final @NotNull Supplier<@NotNull OnlineTimeService> myOnlineTimeServiceGetInstance;

  PhysicalDeviceAsyncSupplier(@Nullable Project project) {
    this(project,
         MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()),
         PhysicalDeviceAsyncSupplier::getAdb,
         PhysicalDeviceAsyncSupplier::getDebugBridge,
         OnlineTimeService::getInstance);
  }

  @VisibleForTesting
  PhysicalDeviceAsyncSupplier(@Nullable Project project,
                              @NotNull ListeningExecutorService service,
                              @NotNull Function<@Nullable Project, @NotNull Path> getAdb,
                              @NotNull AsyncFunction<@NotNull Path, @Nullable AndroidDebugBridge> getDebugBridge,
                              @NotNull Supplier<@NotNull OnlineTimeService> onlineTimeServiceGetInstance) {
    myProject = project;
    myService = service;
    myGetAdb = getAdb;
    myGetDebugBridge = getDebugBridge;
    myOnlineTimeServiceGetInstance = onlineTimeServiceGetInstance;
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull Path getAdb(@Nullable Project project) {
    return Objects.requireNonNull(AndroidSdkUtils.getAdb(project)).toPath();
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull ListenableFuture<@Nullable AndroidDebugBridge> getDebugBridge(@NotNull Path adb) {
    return AdbService.getInstance().getDebugBridge(adb.toFile());
  }

  @NotNull ListenableFuture<@NotNull List<@NotNull PhysicalDevice>> get() {
    // noinspection UnstableApiUsage
    return FluentFuture.from(myService.submit(() -> myGetAdb.apply(myProject)))
      .transformAsync(myGetDebugBridge, myService)
      .transform(PhysicalDeviceAsyncSupplier::getDevices, myService)
      .transformAsync(this::collectToPhysicalDevices, myService);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull Collection<@NotNull IDevice> getDevices(@Nullable AndroidDebugBridge bridge) {
    if (bridge == null) {
      throw new NullPointerException();
    }

    if (!bridge.isConnected()) {
      throw new IllegalArgumentException();
    }

    return Arrays.asList(bridge.getDevices());
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull ListenableFuture<@NotNull List<@NotNull PhysicalDevice>> collectToPhysicalDevices(@NotNull Collection<@NotNull IDevice> devices) {
    Iterable<ListenableFuture<PhysicalDevice>> futures = devices.stream()
      .filter(device -> !device.isEmulator())
      .map(this::buildPhysicalDevice)
      .collect(Collectors.toList());

    // noinspection UnstableApiUsage
    return FluentFuture.from(Futures.successfulAsList(futures)).transform(PhysicalDeviceAsyncSupplier::filterSuccessful, myService);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull ListenableFuture<@NotNull PhysicalDevice> buildPhysicalDevice(@NotNull IDevice device) {
    ListenableFuture<String> modelFuture = getSystemProperty(device, IDevice.PROP_DEVICE_MODEL);
    ListenableFuture<String> manufacturerFuture = getSystemProperty(device, IDevice.PROP_DEVICE_MANUFACTURER);

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(modelFuture, manufacturerFuture)
      .call(() -> buildPhysicalDevice(device, modelFuture, manufacturerFuture), myService);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull ListenableFuture<@NotNull String> getSystemProperty(@NotNull IShellEnabledDevice device, @NotNull String property) {
    // noinspection UnstableApiUsage
    return JdkFutureAdapters.listenInPoolThread(device.getSystemProperty(property), myService);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull PhysicalDevice buildPhysicalDevice(@NotNull IDevice device,
                                                      @NotNull Future<@NotNull String> modelFuture,
                                                      @NotNull Future<@NotNull String> manufacturerFuture) {
    String serialNumber = device.getSerialNumber();

    return new PhysicalDevice.Builder()
      .setSerialNumber(serialNumber)
      .setLastOnlineTime(myOnlineTimeServiceGetInstance.get().get(serialNumber))
      .setName(DeviceNameProperties.getName(FutureUtils.getDoneOrNull(modelFuture), FutureUtils.getDoneOrNull(manufacturerFuture)))
      .setOnline(true)
      .build();
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull List<@NotNull PhysicalDevice> filterSuccessful(@NotNull Collection<@Nullable PhysicalDevice> devices) {
    List<PhysicalDevice> filteredDevices = devices.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (filteredDevices.size() != devices.size()) {
      Logger.getInstance(PhysicalDeviceAsyncSupplier.class).warn("Some of the physical devices were not successfully built");
    }

    return filteredDevices;
  }
}
