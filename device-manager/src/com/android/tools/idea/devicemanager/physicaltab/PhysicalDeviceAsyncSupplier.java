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
import com.android.ddmlib.IDevice;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceAsyncSupplier {
  private final @Nullable Project myProject;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;
  private final @NotNull Supplier<BuilderService> myBuilderServiceGetInstance;

  @UiThread
  PhysicalDeviceAsyncSupplier(@Nullable Project project) {
    this(project, new DeviceManagerAndroidDebugBridge(), BuilderService::getInstance);
  }

  @VisibleForTesting
  PhysicalDeviceAsyncSupplier(@Nullable Project project,
                              @NotNull DeviceManagerAndroidDebugBridge bridge,
                              @NotNull Supplier<BuilderService> builderServiceGetInstance) {
    myProject = project;
    myBridge = bridge;
    myBuilderServiceGetInstance = builderServiceGetInstance;
  }

  @UiThread
  @NotNull ListenableFuture<List<PhysicalDevice>> get() {
    // noinspection UnstableApiUsage
    return Futures.transformAsync(myBridge.getDevices(myProject), this::collectToPhysicalDevices, EdtExecutorService.getInstance());
  }

  @UiThread
  private @NotNull ListenableFuture<List<PhysicalDevice>> collectToPhysicalDevices(@NotNull Collection<IDevice> devices) {
    BuilderService service = myBuilderServiceGetInstance.get();

    Iterable<ListenableFuture<PhysicalDevice>> futures = devices.stream()
      .filter(device -> !device.isEmulator())
      .map(service::build)
      .collect(Collectors.toList());

    return DeviceManagerFutures.successfulAsList(futures, EdtExecutorService.getInstance());
  }
}
