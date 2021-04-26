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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceAsyncSupplier {
  private final @Nullable Project myProject;
  private final @NotNull Function<@Nullable Project, @NotNull Path> myGetAdb;
  private final @NotNull AsyncFunction<@NotNull Path, @NotNull AndroidDebugBridge> myGetDebugBridge;
  private final @NotNull Supplier<@NotNull BuilderService> myBuilderServiceGetInstance;

  @UiThread
  PhysicalDeviceAsyncSupplier(@Nullable Project project) {
    this(project, PhysicalDeviceAsyncSupplier::getAdb, PhysicalDeviceAsyncSupplier::getDebugBridge, BuilderService::getInstance);
  }

  @VisibleForTesting
  PhysicalDeviceAsyncSupplier(@Nullable Project project,
                              @NotNull Function<@Nullable Project, @NotNull Path> getAdb,
                              @NotNull AsyncFunction<@NotNull Path, @NotNull AndroidDebugBridge> getDebugBridge,
                              @NotNull Supplier<@NotNull BuilderService> builderServiceGetInstance) {
    myProject = project;
    myGetAdb = getAdb;
    myGetDebugBridge = getDebugBridge;
    myBuilderServiceGetInstance = builderServiceGetInstance;
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
  private static @NotNull ListenableFuture<@NotNull AndroidDebugBridge> getDebugBridge(@NotNull Path adb) {
    return AdbService.getInstance().getDebugBridge(adb.toFile());
  }

  @UiThread
  @NotNull ListenableFuture<@NotNull List<@NotNull PhysicalDevice>> get() {
    ListeningExecutorService service = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService());

    // noinspection UnstableApiUsage
    return FluentFuture.from(service.submit(() -> myGetAdb.apply(myProject)))
      .transformAsync(myGetDebugBridge, service)
      .transform(PhysicalDeviceAsyncSupplier::getDevices, service)
      .transformAsync(this::collectToPhysicalDevices, EdtExecutorService.getInstance());
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private static @NotNull Collection<@NotNull IDevice> getDevices(@NotNull AndroidDebugBridge bridge) {
    if (!bridge.isConnected()) {
      throw new IllegalArgumentException();
    }

    return Arrays.asList(bridge.getDevices());
  }

  @UiThread
  private @NotNull ListenableFuture<@NotNull List<@NotNull PhysicalDevice>> collectToPhysicalDevices(@NotNull Collection<@NotNull IDevice> devices) {
    BuilderService service = myBuilderServiceGetInstance.get();

    Iterable<ListenableFuture<PhysicalDevice>> futures = devices.stream()
      .filter(device -> !device.isEmulator())
      .map(service::build)
      .collect(Collectors.toList());

    // noinspection UnstableApiUsage
    return FluentFuture.from(Futures.successfulAsList(futures))
      .transform(PhysicalDeviceAsyncSupplier::filterSuccessful, EdtExecutorService.getInstance());
  }

  @UiThread
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
