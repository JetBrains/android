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
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.Resolution;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncVirtualDeviceDetailsBuilder {
  private final @Nullable Project myProject;
  private final @NotNull VirtualDevice myDevice;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;

  AsyncVirtualDeviceDetailsBuilder(@Nullable Project project, @NotNull VirtualDevice device) {
    this(project, device, new DeviceManagerAndroidDebugBridge());
  }

  @VisibleForTesting
  AsyncVirtualDeviceDetailsBuilder(@Nullable Project project,
                                   @NotNull VirtualDevice device,
                                   @NotNull DeviceManagerAndroidDebugBridge bridge) {
    myProject = project;
    myDevice = device;
    myBridge = bridge;
  }

  @NotNull ListenableFuture<@NotNull Device> buildAsync() {
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

  private @NotNull Device build(@Nullable IDevice device) {
    AvdInfo avd = myDevice.getAvdInfo();

    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setKey(myDevice.getKey())
      .setName(myDevice.getName())
      .setTarget(myDevice.getTarget())
      .setCpuArchitecture(myDevice.getCpuArchitecture())
      .setResolution(getResolution(avd))
      .setDensity(getProperty(avd, "hw.lcd.density").orElse(-1));

    if (device != null) {
      builder.addAllAbis(device.getAbis());
    }

    return builder
      .setAvdInfo(avd)
      .build();
  }

  private static @Nullable Resolution getResolution(@NotNull AvdInfo avd) {
    OptionalInt width = getProperty(avd, "hw.lcd.width");
    OptionalInt height = getProperty(avd, "hw.lcd.height");

    if (width.isEmpty() || height.isEmpty()) {
      return null;
    }

    return new Resolution(width.orElseThrow(), height.orElseThrow());
  }

  private static @NotNull OptionalInt getProperty(@NotNull AvdInfo avd, @NotNull String name) {
    String property = avd.getProperty(name);

    if (property == null) {
      return OptionalInt.empty();
    }

    try {
      return OptionalInt.of(Integer.parseInt(property));
    }
    catch (NumberFormatException exception) {
      return OptionalInt.empty();
    }
  }

  @Nullable Project getProject() {
    return myProject;
  }
}
