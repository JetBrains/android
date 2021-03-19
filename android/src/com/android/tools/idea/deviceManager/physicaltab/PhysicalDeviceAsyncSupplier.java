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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceAsyncSupplier {
  private final @Nullable Project myProject;
  private final @NotNull ListeningExecutorService myService;
  private final @NotNull Function<@Nullable Project, @NotNull Path> myGetAdb;
  private final @NotNull AsyncFunction<@NotNull Path, @Nullable AndroidDebugBridge> myGetDebugBridge;

  PhysicalDeviceAsyncSupplier(@Nullable Project project) {
    this(project,
         MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()),
         PhysicalDeviceAsyncSupplier::getAdb,
         PhysicalDeviceAsyncSupplier::getDebugBridge);
  }

  @VisibleForTesting
  PhysicalDeviceAsyncSupplier(@Nullable Project project,
                              @NotNull ListeningExecutorService service,
                              @NotNull Function<@Nullable Project, @NotNull Path> getAdb,
                              @NotNull AsyncFunction<@NotNull Path, @Nullable AndroidDebugBridge> getDebugBridge) {
    myProject = project;
    myService = service;
    myGetAdb = getAdb;
    myGetDebugBridge = getDebugBridge;
  }

  /**
   * Called by a pooled application thread
   */
  private static @NotNull Path getAdb(@Nullable Project project) {
    return Objects.requireNonNull(AndroidSdkUtils.getAdb(project)).toPath();
  }

  /**
   * Called by a pooled application thread
   */
  private static @NotNull ListenableFuture<@Nullable AndroidDebugBridge> getDebugBridge(@NotNull Path adb) {
    return AdbService.getInstance().getDebugBridge(adb.toFile());
  }

  @NotNull ListenableFuture<@NotNull List<@NotNull PhysicalDevice>> get() {
    // noinspection UnstableApiUsage
    return FluentFuture.from(myService.submit(() -> myGetAdb.apply(myProject)))
      .transformAsync(myGetDebugBridge, myService)
      .transform(PhysicalDeviceAsyncSupplier::getDevices, myService)
      .transform(PhysicalDeviceAsyncSupplier::collectToPhysicalDevices, myService);
  }

  /**
   * Called by a pooled application thread
   */
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
   * Called by a pooled application thread
   */
  private static @NotNull List<@NotNull PhysicalDevice> collectToPhysicalDevices(@NotNull Collection<@NotNull IDevice> devices) {
    return devices.stream()
      .filter(device -> !device.isEmulator())
      .map(IDevice::getSerialNumber)
      .map(PhysicalDevice::new)
      .collect(Collectors.toList());
  }
}
