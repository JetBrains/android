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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncVirtualDeviceDetailsBuilder {
  private final @Nullable Project myProject;
  private final @NotNull VirtualDevice myDevice;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;

  @VisibleForTesting
  AsyncVirtualDeviceDetailsBuilder(@Nullable Project project,
                                   @NotNull VirtualDevice device,
                                   @NotNull DeviceManagerAndroidDebugBridge bridge) {
    myProject = project;
    myDevice = device;
    myBridge = bridge;
  }

  @NotNull Future<@NotNull Object> buildAsync() {
    Executor executor = AppExecutorUtil.getAppExecutorService();

    // noinspection UnstableApiUsage
    return FluentFuture.from(myBridge.getDevices(myProject))
      .transformAsync(this::findDevice, executor)
      .transform(this::build, executor);
  }

  private @NotNull ListenableFuture<@NotNull IDevice> findDevice(@NotNull List<@NotNull IDevice> devices) {
    Iterable<ListenableFuture<AvdData>> futures = devices.stream()
      .map(IDevice::getAvdData)
      .collect(Collectors.toList());

    // noinspection UnstableApiUsage
    return Futures.transform(Futures.successfulAsList(futures), avds -> findDevice(avds, devices), AppExecutorUtil.getAppExecutorService());
  }

  private @Nullable IDevice findDevice(@NotNull List<@Nullable AvdData> avds, @NotNull List<@NotNull IDevice> devices) {
    Object key = myDevice.getKey().toString();

    for (int i = 0, size = avds.size(); i < size; i++) {
      AvdData avd = avds.get(i);

      if (avd == null) {
        continue;
      }

      if (Objects.equals(avd.getPath(), key)) {
        return devices.get(i);
      }
    }

    return null;
  }

  private @NotNull Object build(@Nullable IDevice device) {
    return new VirtualDevice.Builder()
      .setKey(myDevice.getKey())
      .setType(myDevice.getType())
      .setName(myDevice.getName())
      .setOnline(myDevice.isOnline())
      .setTarget(myDevice.getTarget())
      .setCpuArchitecture(myDevice.getCpuArchitecture())
      .setAndroidVersion(myDevice.getAndroidVersion())
      .setSizeOnDisk(myDevice.getSizeOnDisk())
      .setAvdInfo(myDevice.getAvdInfo())
      .build();
  }
}
